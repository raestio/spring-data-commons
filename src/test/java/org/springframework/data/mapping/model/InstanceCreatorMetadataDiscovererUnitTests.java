/*
 * Copyright 2021-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mapping.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mapping.InstanceCreatorMetadata;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.util.ClassTypeInformation;

/**
 * Unit tests for {@link InstanceCreatorMetadataDiscoverer}.
 *
 * @author Mark Paluch
 */
class InstanceCreatorMetadataDiscovererUnitTests {

	@Test
	void shouldDiscoverAnnotatedFactoryMethod() {

		PersistentEntity<FactoryMethodsPerson, ?> entity = new BasicPersistentEntity<>(
				ClassTypeInformation.from(FactoryMethodsPerson.class));
		InstanceCreatorMetadata<?> creator = InstanceCreatorMetadataDiscoverer.discover(entity);

		assertThat(creator).isInstanceOf(org.springframework.data.mapping.FactoryMethod.class);
		assertThat(((org.springframework.data.mapping.FactoryMethod<?, ?>) creator).getFactoryMethod().getParameterCount())
				.isEqualTo(2);
	}

	@Test
	void shouldDiscoverAnnotatedConstructor() {

		PersistentEntity<ConstructorPerson, ?> entity = new BasicPersistentEntity<>(
				ClassTypeInformation.from(ConstructorPerson.class));
		InstanceCreatorMetadata<?> creator = InstanceCreatorMetadataDiscoverer.discover(entity);

		assertThat(creator).isInstanceOf(PreferredConstructor.class);
	}

	@Test
	void shouldDiscoverDefaultConstructor() {

		PersistentEntity<Person, ?> entity = new BasicPersistentEntity<>(ClassTypeInformation.from(Person.class));
		InstanceCreatorMetadata<?> creator = InstanceCreatorMetadataDiscoverer.discover(entity);

		assertThat(creator).isInstanceOf(PreferredConstructor.class);
	}

	@Test
	void shouldRejectNonStaticFactoryMethod() {
		assertThatExceptionOfType(MappingException.class)
				.isThrownBy(() -> new BasicPersistentEntity<>(ClassTypeInformation.from(NonStaticFactoryMethod.class)));
	}

	static class Person {

		private final String firstname, lastname;

		private Person(String firstname, String lastname) {
			this.firstname = firstname;
			this.lastname = lastname;
		}

	}

	static class NonStaticFactoryMethod {

		@PersistenceCreator
		public ConstructorPerson of(String firstname, String lastname) {
			return new ConstructorPerson(firstname, lastname);
		}

	}

	static class FactoryMethodsPerson {

		private final String firstname, lastname;

		private FactoryMethodsPerson(String firstname, String lastname) {
			this.firstname = firstname;
			this.lastname = lastname;
		}

		public static FactoryMethodsPerson of(String firstname) {
			return new FactoryMethodsPerson(firstname, "unknown");
		}

		@PersistenceCreator
		public static FactoryMethodsPerson of(String firstname, String lastname) {
			return new FactoryMethodsPerson(firstname, lastname);
		}
	}

	static class ConstructorPerson {

		private final String firstname, lastname;

		private ConstructorPerson(String firstname, String lastname) {
			this.firstname = firstname;
			this.lastname = lastname;
		}

		public static ConstructorPerson of(String firstname, String lastname) {
			return new ConstructorPerson(firstname, lastname);
		}
	}
}
