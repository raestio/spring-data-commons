/*
 * Copyright 2011-2022 the original author or authors.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.util.Lazy;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Simple implementation of {@link PersistentProperty}.
 *
 * @author Jon Brisbin
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public abstract class AbstractPersistentProperty<P extends PersistentProperty<P>> implements PersistentProperty<P> {

	private static final Field CAUSE_FIELD;
	private static final Class<?> ASSOCIATION_TYPE;

	static {

		CAUSE_FIELD = ReflectionUtils.findRequiredField(Throwable.class, "cause");
		ASSOCIATION_TYPE = ReflectionUtils.loadIfPresent("org.jmolecules.ddd.types.Association",
				AbstractPersistentProperty.class.getClassLoader());
	}

	private final String name;
	private final TypeInformation<?> information;
	private final Class<?> rawType;
	private final Lazy<Association<P>> association;
	private final PersistentEntity<?, P> owner;
	private final Property property;
	private final Lazy<Integer> hashCode;
	private final Lazy<Boolean> usePropertyAccess;
	private final Lazy<Set<TypeInformation<?>>> entityTypeInformation;

	private final Lazy<Boolean> isAssociation;
	private final Lazy<TypeInformation<?>> associationTargetType;

	private final Method getter;
	private final Method setter;
	private final Field field;
	private final Method wither;
	private final boolean immutable;

	public AbstractPersistentProperty(Property property, PersistentEntity<?, P> owner,
			SimpleTypeHolder simpleTypeHolder) {

		Assert.notNull(simpleTypeHolder, "SimpleTypeHolder must not be null");
		Assert.notNull(owner, "Owner entity must not be null");

		this.name = property.getName();
		this.information = owner.getTypeInformation().getRequiredProperty(getName());
		this.rawType = this.information.getType();
		this.property = property;
		this.association = Lazy.of(() -> isAssociation() ? createAssociation() : null);
		this.owner = owner;

		this.hashCode = Lazy.of(property::hashCode);
		this.usePropertyAccess = Lazy.of(() -> owner.getType().isInterface() || CAUSE_FIELD.equals(getField()));

		this.isAssociation = Lazy.of(() -> ASSOCIATION_TYPE != null && ASSOCIATION_TYPE.isAssignableFrom(rawType));
		this.associationTargetType = ASSOCIATION_TYPE == null //
				? Lazy.empty() //
				: Lazy.of(() -> Optional.of(getTypeInformation()) //
						.map(it -> it.getSuperTypeInformation(ASSOCIATION_TYPE)) //
						.map(TypeInformation::getComponentType) //
						.orElse(null));

		this.entityTypeInformation = Lazy.of(() -> detectEntityTypes(simpleTypeHolder));

		this.getter = property.getGetter().orElse(null);
		this.setter = property.getSetter().orElse(null);
		this.field = property.getField().orElse(null);
		this.wither = property.getWither().orElse(null);

		if (setter == null && (field == null || Modifier.isFinal(field.getModifiers()))) {
			this.immutable = true;
		} else {
			this.immutable = false;
		}
	}

	protected abstract Association<P> createAssociation();

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getOwner()
	 */
	@Override
	public PersistentEntity<?, P> getOwner() {
		return this.owner;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getType()
	 */
	@Override
	public Class<?> getType() {
		return information.getType();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getRawType()
	 */
	@Override
	public Class<?> getRawType() {
		return this.rawType;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getTypeInformation()
	 */
	@Override
	public TypeInformation<?> getTypeInformation() {
		return information;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getPersistentEntityTypes()
	 */
	@Override
	public Iterable<? extends TypeInformation<?>> getPersistentEntityTypes() {
		return getPersistentEntityTypeInformation();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getPersistentEntityTypeInformation()
	 */
	@Override
	public Iterable<? extends TypeInformation<?>> getPersistentEntityTypeInformation() {

		if (isMap() || isCollectionLike()) {
			return entityTypeInformation.get();
		}

		if (!isEntity()) {
			return Collections.emptySet();
		}

		return entityTypeInformation.get();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getGetter()
	 */
	@Nullable
	@Override
	public Method getGetter() {
		return this.getter;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getSetter()
	 */
	@Nullable
	@Override
	public Method getSetter() {
		return this.setter;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getWither()
	 */
	@Nullable
	@Override
	public Method getWither() {
		return this.wither;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getField()
	 */
	@Nullable
	public Field getField() {
		return this.field;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getSpelExpression()
	 */
	@Override
	@Nullable
	public String getSpelExpression() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isTransient()
	 */
	@Override
	public boolean isTransient() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isWritable()
	 */
	@Override
	public boolean isWritable() {
		return !isTransient();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isImmutable()
	 */
	@Override
	public boolean isImmutable() {
		return immutable;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isAssociation()
	 */
	@Override
	public boolean isAssociation() {
		return isAssociation.get();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getAssociation()
	 */
	@Nullable
	@Override
	public Association<P> getAssociation() {
		return association.orElse(null);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getAssociationTargetType()
	 */
	@Nullable
	@Override
	public Class<?> getAssociationTargetType() {

		TypeInformation<?> result = getAssociationTargetTypeInformation();

		return result != null ? result.getType() : null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getAssociationTargetTypeInformation()
	 */
	@Nullable
	@Override
	public TypeInformation<?> getAssociationTargetTypeInformation() {
		return associationTargetType.getNullable();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isCollectionLike()
	 */
	@Override
	public boolean isCollectionLike() {
		return information.isCollectionLike();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isMap()
	 */
	@Override
	public boolean isMap() {
		return Map.class.isAssignableFrom(getType());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isArray()
	 */
	@Override
	public boolean isArray() {
		return getType().isArray();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#isEntity()
	 */
	@Override
	public boolean isEntity() {
		return !isTransient() && !entityTypeInformation.get().isEmpty();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getComponentType()
	 */
	@Nullable
	@Override
	public Class<?> getComponentType() {
		return isMap() || isCollectionLike() ? information.getRequiredComponentType().getType() : null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getMapValueType()
	 */
	@Nullable
	@Override
	public Class<?> getMapValueType() {

		if (isMap()) {

			TypeInformation<?> mapValueType = information.getMapValueType();

			if (mapValueType != null) {
				return mapValueType.getType();
			}
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.PersistentProperty#getActualType()
	 */
	@Override
	public Class<?> getActualType() {
		return getActualTypeInformation().getType();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.mapping.MongoPersistentProperty#usePropertyAccess()
	 */
	public boolean usePropertyAccess() {
		return usePropertyAccess.get();
	}

	protected Property getProperty() {
		return this.property;
	}

	protected TypeInformation<?> getActualTypeInformation() {

		TypeInformation<?> targetType = associationTargetType.getNullable();
		return targetType == null ? information.getRequiredActualType() : targetType;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(@Nullable Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof AbstractPersistentProperty)) {
			return false;
		}

		AbstractPersistentProperty<?> that = (AbstractPersistentProperty<?>) obj;

		return this.property.equals(that.property);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.hashCode.get();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return property.toString();
	}

	private Set<TypeInformation<?>> detectEntityTypes(SimpleTypeHolder simpleTypes) {

		TypeInformation<?> typeToStartWith = getAssociationTargetTypeInformation();
		typeToStartWith = typeToStartWith == null ? information : typeToStartWith;

		Set<TypeInformation<?>> result = detectEntityTypes(typeToStartWith);

		return result.stream()
				.filter(it -> !simpleTypes.isSimpleType(it.getType()))
				.filter(it -> !it.getType().equals(ASSOCIATION_TYPE))
				.collect(Collectors.toSet());
	}

	private Set<TypeInformation<?>> detectEntityTypes(@Nullable TypeInformation<?> source) {

		if (source == null) {
			return Collections.emptySet();
		}

		Set<TypeInformation<?>> result = new HashSet<>();

		if (source.isMap()) {
			result.addAll(detectEntityTypes(source.getComponentType()));
		}

		TypeInformation<?> actualType = source.getActualType();

		if (source.equals(actualType)) {
			result.add(source);
		} else {
			result.addAll(detectEntityTypes(actualType));
		}

		return result;
	}
}
