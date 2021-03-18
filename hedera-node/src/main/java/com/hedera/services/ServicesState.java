package com.hedera.services;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.exceptions.ContextNotFoundException;
import com.hedera.services.legacy.stream.RecordStream;
import com.hedera.services.sigs.sourcing.ScopedSigBytesProvider;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleNftOwnership;
import com.hedera.services.state.merkle.MerkleNftType;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldState;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.services.state.merkle.MerkleNetworkContext.UNKNOWN_CONSENSUS_TIME;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;

public class ServicesState extends AbstractNaryMerkleInternal implements SwirldState.SwirldState2 {
	private static final Logger log = LogManager.getLogger(ServicesState.class);

	private static final ImmutableHash emptyHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int RELEASE_090_VERSION = 3;
	static final int RELEASE_0100_VERSION = 4;
	static final int RELEASE_0110_VERSION = 5;
	static final int RELEASE_0120_VERSION = 6;
	static final int RELEASE_0140_VERSION = 8;
	static final int MERKLE_VERSION = RELEASE_0140_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;

	static final String UNSUPPORTED_VERSION_MSG_TPL = "Argument 'version=%d' is invalid!";

	static Function<String, byte[]> hashReader = RecordStream::readPrevFileHash;
	static Consumer<MerkleNode> merkleDigest = CryptoFactory.getInstance()::digestTreeSync;
	static Supplier<BinaryObjectStore> blobStoreSupplier = BinaryObjectStore::getInstance;

	NodeId nodeId = null;
	boolean skipDiskFsHashCheck = false;

	/* Order of Merkle node children */
	static class ChildIndices {
		static final int ADDRESS_BOOK = 0;
		static final int NETWORK_CTX = 1;
		static final int TOPICS = 2;
		static final int STORAGE = 3;
		static final int ACCOUNTS = 4;
		static final int NUM_070_CHILDREN = 5;
		static final int TOKENS = 5;
		static final int NUM_080_CHILDREN = 6;
		static final int TOKEN_ASSOCIATIONS = 6;
		static final int DISK_FS = 7;
		static final int NUM_090_CHILDREN = 8;
		static final int NUM_0100_CHILDREN = 8;
		static final int SCHEDULE_TXS = 8;
		static final int RECORD_STREAM_RUNNING_HASH = 9;
		static final int NUM_0110_CHILDREN = 10;
		static final int NUM_0120_CHILDREN = 10;
		static final int NFT_TYPES = 10;
		static final int NFT_OWNERSHIPS = 11;
		static final int NUM_0140_CHILDREN = 12;
	}

	ServicesContext ctx;

	public ServicesState() {
	}

	public ServicesState(List<MerkleNode> children) {
		super(ChildIndices.NUM_0140_CHILDREN);
		addDeserializedChildren(children, MERKLE_VERSION);
	}

	public ServicesState(ServicesContext ctx, NodeId nodeId, List<MerkleNode> children) {
		this(children);
		this.ctx = ctx;
		this.nodeId = nodeId;
		if (ctx != null) {
			ctx.update(this);
		}
	}

	/* --- MerkleInternal --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public int getMinimumChildCount(int version) {
		switch (version) {
			case RELEASE_0140_VERSION:
				return ChildIndices.NUM_0140_CHILDREN;
			case RELEASE_0120_VERSION:
				return ChildIndices.NUM_0120_CHILDREN;
			case RELEASE_0110_VERSION:
				return ChildIndices.NUM_0110_CHILDREN;
			case RELEASE_0100_VERSION:
				return ChildIndices.NUM_0100_CHILDREN;
			case RELEASE_090_VERSION:
				return ChildIndices.NUM_090_CHILDREN;
			case RELEASE_080_VERSION:
				return ChildIndices.NUM_080_CHILDREN;
			case RELEASE_070_VERSION:
				return ChildIndices.NUM_070_CHILDREN;
			default:
				throw new IllegalArgumentException(String.format(UNSUPPORTED_VERSION_MSG_TPL, version));
		}
	}

	@Override
	public void initialize() {
		if (tokens() == null) {
			setChild(ChildIndices.TOKENS,
					new FCMap<>());
			log.info("Created tokens FCMap after 0.7.0 state restoration");
		}
		if (tokenAssociations() == null) {
			setChild(ChildIndices.TOKEN_ASSOCIATIONS, new FCMap<>());
			log.info("Created token associations FCMap after <=0.8.0 state restoration");
		}
		if (diskFs() == null) {
			setChild(ChildIndices.DISK_FS, new MerkleDiskFs());
			log.info("Created disk file system after <=0.9.0 state restoration");
			skipDiskFsHashCheck = true;
		}
		if (scheduleTxs() == null) {
			setChild(ChildIndices.SCHEDULE_TXS, new FCMap<>());
			log.info("Created scheduled transactions FCMap after <=0.10.0 state restoration");
		}
		if (runningHashLeaf() == null) {
			initializeRunningHashTo(emptyHash);
			log.info("Created RecordsRunningHashLeaf after <=0.11.0 state restoration");
		}
		if (nftTypes() == null) {
			setChild(ChildIndices.NFT_TYPES, new FCMap<>());
		}
		if (nftOwnerships() == null) {
			setChild(ChildIndices.NFT_OWNERSHIPS, new FCMap<>());
		}
	}

	@Override
	public void genesisInit(Platform platform, AddressBook addressBook) {
		this.init(platform, addressBook);
	}

	/* --- SwirldState --- */
	@Override
	public void init(Platform platform, AddressBook addressBook) {
		setImmutable(false);
		nodeId = platform.getSelfId();

		/* Note this overrides the address book from the saved state if it is present. */
		setChild(ChildIndices.ADDRESS_BOOK, addressBook);

		var bootstrapProps = new BootstrapProperties();
		var diskFsBaseDirPath = bootstrapProps.getStringProperty("files.diskFsBaseDir.path");
		var properties = new StandardizedPropertySources(bootstrapProps, loc -> new File(loc).exists());
		try {
			ctx = CONTEXTS.lookup(nodeId.getId());
		} catch (ContextNotFoundException ignoreToInstantiateNewContext) {
			ctx = new ServicesContext(nodeId, platform, this, properties);
		}
		boolean initWithMerkle = true;
		if (getNumberOfChildren() < ChildIndices.NUM_0120_CHILDREN) {
			initWithMerkle = false;
			log.info("Init called on Services node {} WITHOUT Merkle saved state", nodeId);
			long seqStart = bootstrapProps.getLongProperty("hedera.numReservedSystemEntities") + 1;
			setChild(ChildIndices.NETWORK_CTX,
					new MerkleNetworkContext(
							UNKNOWN_CONSENSUS_TIME,
							new SequenceNumber(seqStart),
							new ExchangeRates()));
			setChild(ChildIndices.TOPICS, new FCMap<>());
			setChild(ChildIndices.STORAGE, new FCMap<>());
			setChild(ChildIndices.ACCOUNTS, new FCMap<>());
			setChild(ChildIndices.TOKENS, new FCMap<>());
			setChild(ChildIndices.TOKEN_ASSOCIATIONS, new FCMap<>());
			setChild(ChildIndices.DISK_FS, new MerkleDiskFs(diskFsBaseDirPath, asLiteralString(ctx.nodeAccount())));
			setChild(ChildIndices.SCHEDULE_TXS, new FCMap<>());
			setChild(ChildIndices.NFT_TYPES, new FCMap<>());
			setChild(ChildIndices.NFT_OWNERSHIPS, new FCMap<>());
			initializeRunningHashTo(emptyHash);
		} else {
			log.info("Init called on Services node {} WITH Merkle saved state", nodeId);

			/* In a network where two or more nodes run on the same computer, each node's disk-based
			file system must use a different path. So we update the object that Platform
			constructed from state with this node's account, which the DiskFs will use to scope the
			path to its disk-based storage. */
			var restoredDiskFs = diskFs();
			restoredDiskFs.setFsBaseDir(diskFsBaseDirPath);
			restoredDiskFs.setFsNodeScopedDir(asLiteralString(ctx.nodeAccount()));
			if (!skipDiskFsHashCheck) {
				restoredDiskFs.checkHashesAgainstDiskContents();
			}
		}

		if (runningHashIsAbInitio()) {
			byte[] hashFromDisk = hashReader.apply(ctx.getRecordStreamDirectory(ctx.nodeLocalProperties()));
			if (!Arrays.equals(hashFromDisk, emptyHash.getValue())) {
				initializeRunningHashTo(new ImmutableHash(hashFromDisk));
			}
		}
		log.info("initial Hash in RecordsRunningHashLeaf: {}", () -> runningHashLeaf().getRunningHash().getHash());
		// set records initialHash
		ctx.setRecordsInitialHash(runningHashLeaf().getRunningHash().getHash());

		if (initWithMerkle) {
			merkleDigest.accept(this);
			printHashes();
		}

		initializeContext(ctx);
		CONTEXTS.store(ctx);

		log.info("  --> Context initialized accordingly on Services node {}", nodeId);
		log.info("ServicesState init with {} accounts", () -> this.accounts().size());
		log.info("ServicesState init with {} topics", () -> this.topics().size());
	}

	private void initializeContext(final ServicesContext ctx) {
		/* Set the primitive state in the context and signal the managing stores (if
		 * they are already constructed) to rebuild their auxiliary views of the state.
		 * All the initialization that follows will be a function of the primitive state. */
		ctx.update(this);
		ctx.rebuildBackingStoresIfPresent();
		ctx.rebuildStoreViewsIfPresent();

		/* Use any payer records stored in state to rebuild the recent transaction
		 * history. This history has two main uses: Purging expired records, and
		 * classifying duplicate transactions. */
		ctx.recordsHistorian().reviewExistingRecords();
		/*
		 * Use any entities stored in state to rebuild queue of expired entities.
		 */
		ctx.expiries().restartEntitiesTrackingFrom();
		if (!blobStoreSupplier.get().isInitializing()) {
			ctx.systemFilesManager().loadAllSystemFiles();
		}
	}

	@Override
	public AddressBook getAddressBookCopy() {
		return addressBook().copy();
	}

	@Override
	public synchronized void handleTransaction(
			long submittingMember,
			boolean isConsensus,
			Instant creationTime,
			Instant consensusTime,
			com.swirlds.common.Transaction transaction
	) {
		if (isConsensus) {
			ctx.logic().incorporateConsensusTxn(transaction, consensusTime, submittingMember);
		}
	}

	@Override
	public void expandSignatures(Transaction platformTxn) {
		try {
			var accessor = new PlatformTxnAccessor(platformTxn);
			expandIn(
					accessor,
					ctx.lookupRetryingKeyOrder(),
					new ScopedSigBytesProvider(accessor),
					ctx.sigFactoryCreator()::createScopedFactory);
		} catch (InvalidProtocolBufferException e) {
			log.warn("expandSignatures called with non-gRPC txn!", e);
		} catch (Exception race) {
			log.warn("Unexpected problem, signatures will be verified synchronously in handleTransaction!", race);
		}
	}

	@Override
	public void noMoreTransactions() {
	}

	/* --- FastCopyable --- */
	@Override
	public synchronized ServicesState copy() {
		setImmutable(true);
		return new ServicesState(ctx, nodeId, List.of(
				addressBook().copy(),
				networkCtx().copy(),
				topics().copy(),
				storage().copy(),
				accounts().copy(),
				tokens().copy(),
				tokenAssociations().copy(),
				diskFs().copy(),
				scheduleTxs().copy(),
				runningHashLeaf().copy(),
				nftTypes().copy(),
				nftOwnerships().copy()
		));
	}

	/* --------------- */

	public AccountID getNodeAccountId() {
		var address = addressBook().getAddress(nodeId.getId());
		var memo = address.getMemo();
		return accountParsedFromString(memo);
	}

	public void printHashes() {
		ServicesMain.log.info(String.format("[SwirldState Hashes]\n" +
						"  Overall           :: %s\n" +
						"  Accounts          :: %s\n" +
						"  Storage           :: %s\n" +
						"  Topics            :: %s\n" +
						"  Tokens            :: %s\n" +
						"  NftTypes          :: %s\n" +
						"  NftOwnerships     :: %s\n" +
						"  TokenAssociations :: %s\n" +
						"  DiskFs            :: %s\n" +
						"  ScheduledTxs      :: %s\n" +
						"  NetworkContext    :: %s\n" +
						"  AddressBook       :: %s\n" +
						"  RecordsRunningHashLeaf:: %s\n" +
						"  running Hash saved in RecordsRunningHashLeaf:: %s",
				getHash(),
				accounts().getHash(),
				storage().getHash(),
				topics().getHash(),
				tokens().getHash(),
				nftTypes().getHash(),
				nftOwnerships().getHash(),
				tokenAssociations().getHash(),
				diskFs().getHash(),
				scheduleTxs().getHash(),
				networkCtx().getHash(),
				addressBook().getHash(),
				runningHashLeaf().getHash(),
				runningHashLeaf().getRunningHash().getHash()));
	}

	public FCMap<MerkleEntityId, MerkleAccount> accounts() {
		return getChild(ChildIndices.ACCOUNTS);
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage() {
		return getChild(ChildIndices.STORAGE);
	}

	public FCMap<MerkleEntityId, MerkleTopic> topics() {
		return getChild(ChildIndices.TOPICS);
	}

	public FCMap<MerkleEntityId, MerkleToken> tokens() {
		return getChild(ChildIndices.TOKENS);
	}

	public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations() {
		return getChild(ChildIndices.TOKEN_ASSOCIATIONS);
	}

	public FCMap<MerkleEntityId, MerkleSchedule> scheduleTxs() {
		return getChild(ChildIndices.SCHEDULE_TXS);
	}

	public MerkleNetworkContext networkCtx() {
		return getChild(ChildIndices.NETWORK_CTX);
	}

	public AddressBook addressBook() {
		return getChild(ChildIndices.ADDRESS_BOOK);
	}

	public MerkleDiskFs diskFs() {
		return getChild((ChildIndices.DISK_FS));
	}

	public RecordsRunningHashLeaf runningHashLeaf() {
		return getChild(ChildIndices.RECORD_STREAM_RUNNING_HASH);
	}

	public FCMap<MerkleNftOwnership, MerkleEntityId> nftOwnerships() {
		return getChild(ChildIndices.NFT_OWNERSHIPS);
	}

	public FCMap<MerkleEntityId, MerkleNftType> nftTypes() {
		return getChild(ChildIndices.NFT_TYPES);
	}

	private void initializeRunningHashTo(Hash lastHash) {
		var runningHash = new RunningHash(lastHash);
		var initialRecordsRunningHashLeaf = new RecordsRunningHashLeaf(runningHash);
		setChild(ChildIndices.RECORD_STREAM_RUNNING_HASH, initialRecordsRunningHashLeaf);
	}

	private boolean runningHashIsAbInitio() {
		return runningHashLeaf().getRunningHash().getHash().equals(emptyHash);
	}
}
