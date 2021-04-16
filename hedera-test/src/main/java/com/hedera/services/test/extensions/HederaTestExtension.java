package com.hedera.services.test.extensions;

import com.hedera.services.test.TestHapiClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.stream.Stream;

public class HederaTestExtension implements TestInstancePostProcessor {
	@Override
	public void postProcessTestInstance(Object o, ExtensionContext extensionContext) throws Exception {
		Class<?> testCls = o.getClass();

		Field client = null;

		for (var field : testCls.getDeclaredFields()) {
			if (isInjectableClient(field)) {
				client = field;
			}
		}

		if (client == null) {
			throw new IllegalStateException("The test class has no TestHapiClient field marked with @Inject");
		}
	}

	private boolean isInjectableClient(Field field) {
		if (!field.getType().equals(TestHapiClient.class)) {
			return false;
		}
		var annotations = field.getDeclaredAnnotations();
		return Stream.of(annotations).anyMatch(a -> a.annotationType().equals(Inject.class));
	}
}
