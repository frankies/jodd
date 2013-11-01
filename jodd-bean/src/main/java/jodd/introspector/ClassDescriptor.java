// Copyright (c) 2003-2013, Jodd Team (jodd.org). All Rights Reserved.

package jodd.introspector;

import jodd.util.ReflectUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Collection;

/**
 * A descriptor class for all methods/fields/constructors of a class.
 * Static methods/fields are ignored.
 * Hash table is pre-built to speed up query.
 * <p>
 * Descriptors are 'lazy': various internal caches are created only on request.
 * <p>
 * Throughout this class, public members are defined as members
 * defined with "public" keyword and declared in a public type.
 * Public members declared by a non-public class is considered non-public
 * because access to it from outside is prohibited by the java access control
 * anyway.
 * <p>
 * Public members defined in public classes are always preferred even
 * when we allow private/protected members and types to be visible.
 * So if a non-public subtype and a public super type both have a field
 * with the same name, the field in the public super type is always used.
 */
public class ClassDescriptor {

	protected final Class type;
	protected final boolean accessibleOnly;
	protected int usageCount;

	public ClassDescriptor(Class type, boolean accessibleOnly) {
		this.type = type;
		isArray = type.isArray();
		isMap = ReflectUtil.isSubclass(type, Map.class);
		isList = ReflectUtil.isSubclass(type, List.class);
		isSet = ReflectUtil.isSubclass(type, Set.class);
		isCollection = ReflectUtil.isSubclass(type, Collection.class);
		this.accessibleOnly = accessibleOnly;
	}

	/**
	 * Get the class object that this descriptor describes.
	 */
	public Class getType() {
		return type;
	}


	/**
	 * Increases descriptor usage.
	 */
	protected void increaseUsageCount() {
		usageCount++;
	}

	/**
	 * Returns number of class description usages.
	 */
	public int getUsageCount() {
		return usageCount;
	}

	// ---------------------------------------------------------------- special

	private final boolean isArray;
	/**
	 * Returns <code>true</code> if class is an array.
	 */
	public boolean isArray() {
		return isArray;
	}

	private final boolean isMap;
	/**
	 * Returns <code>true</code> if class is a <code>Map</code>.
	 */
	public boolean isMap() {
		return isMap;
	}

	private final boolean isList;
	/**
	 * Returns <code>true</code> if class is a <code>List</code>.
	 */
	public boolean isList() {
		return isList;
	}

	private final boolean isSet;
	/**
	 * Returns <code>true</code> if type is a <code>Set</code>.
	 */
	public boolean isSet() {
		return isSet;
	}

	private final boolean isCollection;
	/**
	 * Returns <code>true</code> if type is a collection.
	 */
	public boolean isCollection() {
		return isCollection;
	}

	// ---------------------------------------------------------------- fields

	/**
	 * Creates new {@code FieldDescriptor}.
	 */
	protected FieldDescriptor createFieldDescriptor(Field field) {
		return new FieldDescriptor(this, field);
	}

	protected Fields publicFields;
	protected Fields allFields;

	/**
	 * Inspect class fields and create fields cache.
	 */
	protected void inspectFields() {
		if (allFields != null) {
			return;
		}

		Field[] fields = accessibleOnly ? ReflectUtil.getAccessibleFields(type) : ReflectUtil.getSupportedFields(type);

		Fields publicFields = new Fields(this, fields.length);
		Fields allFields = new Fields(this, fields.length);

		for (Field field : fields) {
			String fName = field.getName();
			if (ReflectUtil.isPublic(field)) {
				publicFields.addField(fName, field);
			}
			ReflectUtil.forceAccess(field);
			allFields.addField(fName, field);
		}

		this.publicFields = publicFields;
		this.allFields = allFields;
	}

	/**
	 * Returns field descriptor.
	 */
	public FieldDescriptor getFieldDescriptor(String name, boolean declared) {
		inspectFields();

		Fields fields = declared ? allFields : publicFields;
		return fields.getFieldDescriptor(name);
	}

	/**
	 * Returns all field descriptors.
	 */
	public FieldDescriptor[] getAllFieldDescriptors(boolean declared) {
		inspectFields();

		Fields fields = declared ? allFields : publicFields;
		return fields.getAllFields();
	}

	// ---------------------------------------------------------------- methods

	/**
	 * Creates new {@code MethodDescriptor}.
	 */
	protected MethodDescriptor createMethodDescriptor(Method method) {
		return new MethodDescriptor(this, method);
	}

	protected Methods publicMethods;
	protected Methods allMethods;

	/**
	 * Inspect methods and create methods cache.
	 */
	protected void inspectMethods() {
		if (allMethods != null) {
			return;
		}

		Method[] methods = accessibleOnly ? ReflectUtil.getAccessibleMethods(type) : ReflectUtil.getSupportedMethods(type);

		Methods publicMethods = new Methods(this, methods.length);
		Methods allMethods = new Methods(this, methods.length);

		for (Method method : methods) {
			String methodName = method.getName();
			if (ReflectUtil.isPublic(method)) {
				publicMethods.addMethod(methodName, method);
			}
			ReflectUtil.forceAccess(method);
			allMethods.addMethod(methodName, method);
		}

		this.allMethods = allMethods;
		this.publicMethods = publicMethods;
	}

	/**
	 * Returns methods count.
	 */
	public int getMethodsCount(boolean declared) {
		inspectMethods();

		Methods methods = declared ? allMethods : publicMethods;
		return methods.getCount();
	}

	/**
	 * Returns the method identified by name or <code>null</code> if not found.
	 *
	 * @param name	method name
	 * @param declared whether to look at non-public ones.
	 */
	public Method getMethod(String name, boolean declared) {
		inspectMethods();

		Methods methods = declared ? allMethods : publicMethods;
		return methods.getMethod(name);
	}

	/**
	 * Returns the method identified by name and parameters.
	 */
	public Method getMethod(String name, Class[] params, boolean declared) {
		inspectMethods();

		Methods methods = declared ? allMethods : publicMethods;
		return methods.getMethod(name, params);
	}

	/**
	 * Returns {@link MethodDescriptor method descriptor} identified by name and parameters.
	 */
	public MethodDescriptor getMethodDescriptor(String name, Class[] params, boolean declared) {
		inspectMethods();

		Methods methods = declared ? allMethods : publicMethods;
		return methods.getMethodDescriptor(name, params);
	}

	/**
	 * Returns an array of all methods with the same name.
	 */
	public Method[] getAllMethods(String name, boolean declared) {
		inspectMethods();

		Methods methods = declared ? allMethods : publicMethods;
		return methods.getAllMethods(name);
	}

	/**
	 * Returns an array of all methods.
	 */
	public Method[] getAllMethods(boolean declared) {
		inspectMethods();

		Methods methods = declared ? allMethods : publicMethods;
		return methods.getAllMethods();
	}

	// ---------------------------------------------------------------- properties

	protected Properties publicProperties;
	protected Properties allProperties;

	/**
	 * Inspect methods and create properties cache.
	 */
	protected void inspectProperties() {
		if (publicProperties != null) {
			return;
		}
		Properties publicProperties = new Properties(this);
		Properties allProperties = new Properties(this);

		Method[] methods = accessibleOnly ? ReflectUtil.getAccessibleMethods(type) : ReflectUtil.getSupportedMethods(type);
		for (Method method : methods) {
			if (Modifier.isStatic(method.getModifiers())) {
				continue;			// ignore static methods
			}

			boolean add = false;
			boolean issetter = false;

			String propertyName = ReflectUtil.getBeanPropertyGetterName(method);
			if (propertyName != null) {
				propertyName = '-' + propertyName;
				add = true;
				issetter = false;
			} else {
				propertyName = ReflectUtil.getBeanPropertySetterName(method);
				if (propertyName != null) {
					propertyName = '+' + propertyName;
					add = true;
					issetter = true;
				}
			}

			if (add == true) {
				if (ReflectUtil.isPublic(method)) {
					publicProperties.addMethod(propertyName, method);

					MethodDescriptor methodDescriptor = getMethodDescriptor(method.getName(), method.getParameterTypes(), false);
					// todo remove substring(1)!
					publicProperties.addProperty(propertyName.substring(1), methodDescriptor, issetter);
				}
				ReflectUtil.forceAccess(method);
				allProperties.addMethod(propertyName, method);

				MethodDescriptor methodDescriptor = getMethodDescriptor(method.getName(), method.getParameterTypes(), true);
				// todo remove substring(1)!
				allProperties.addProperty(propertyName.substring(1), methodDescriptor, issetter);
			}
		}

		this.allProperties = allProperties;
		this.publicProperties = publicProperties;
	}

	public PropertyDescriptor getPropertyDescriptor(String name, boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getProperty(name);
	}

	public MethodDescriptor getPropertySetter(String name, boolean declared) {
		inspectProperties();

		PropertyDescriptor propertyDescriptor = getPropertyDescriptor(name, declared);
		if (propertyDescriptor == null) {
			return null;
		}

		return propertyDescriptor.getWriteMethodDescriptor();
	}

	public MethodDescriptor getPropertyGetter(String name, boolean declared) {
		inspectProperties();

		PropertyDescriptor propertyDescriptor = getPropertyDescriptor(name, declared);
		if (propertyDescriptor == null) {
			return null;
		}

		return propertyDescriptor.getReadMethodDescriptor();
	}

	// TODO REVIEW REVIEW REVIEW

	/**
	 * Returns bean setter identified by name.
	 */
	public Method getBeanSetter(String name, boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getSetters().getMethod(name);
	}

	/**
	 * Returns bean setter {@link MethodDescriptor} identified by name.
	 */
/*
	public MethodDescriptor getBeanSetterMethodDescriptor(String name, boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getSetters().getMethodDescriptor(name);
	}
*/

	/**
	 * Returns an array of all bean setters.
	 */
	public Method[] getAllBeanSetters(boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getSetters().getAllMethods();
	}

	/**
	 * Returns an array of all bean setters names.
	 */
	public String[] getAllBeanSetterNames(boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getSetterNames();
	}

	/**
	 * Returns bean getter identified by name.
	 */
	public Method getBeanGetter(String name, boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getGetters().getMethod(name);
	}

	/**
	 * Returns {@link MethodDescriptor} for bean setter identified by name.
	 */
/*
	public MethodDescriptor getBeanGetterMethodDescriptor(String name, boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getGetters().getMethodDescriptor(name);
	}
*/

	/**
	 * Returns all bean getters.
	 */
	public Method[] getAllBeanGetters(boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getGetters().getAllMethods();
	}

	/**
	 * Returns all bean getters names.
	 */
	public String[] getAllBeanGetterNames(boolean declared) {
		inspectProperties();

		Properties properties = declared ? allProperties : publicProperties;
		return properties.getGetterNames();
	}


	// ---------------------------------------------------------------- ctors

	protected Ctors publicCtors;
	protected Ctors allCtors;

	/**
	 * Inspect class ctors and create ctors cache.
	 */
	protected void inspectCtors() {
		if (allCtors != null) {
			return;
		}
		Ctors publicCtors = new Ctors(this);
		Ctors allCtors = new Ctors(this);

		publicCtors.addCtors(type.getConstructors());
		allCtors.addCtors(type.getDeclaredConstructors());

		Constructor[] ctors = allCtors.getAllCtors();
		for (Constructor ctor : ctors) {
			if (ReflectUtil.isPublic(ctor) == false) {
				ReflectUtil.forceAccess(ctor);
			}
		}

		this.publicCtors = publicCtors;
		this.allCtors = allCtors;
	}

	/**
	 * Returns the default ctor or <code>null</code> if not found.
	 */
	public Constructor getDefaultCtor(boolean declared) {
		inspectCtors();

		Ctors ctors = declared ? allCtors : publicCtors;
		return ctors.getDefaultCtor();
	}

	/**
	 * Returns the constructor identified by arguments or <code>null</code> if not found.
	 *
	 * @param args	ctor arguments
	 * @param declared whether to look at non-public ones.
	 */
	public Constructor getCtor(Class[] args, boolean declared) {
		inspectCtors();

		Ctors ctors = declared ? allCtors : publicCtors;
		return ctors.getCtor(args);
	}

	/**
	 * Returns the public default ctor or <code>null</code> if not found.
	 */
	public Constructor getDefaultCtor() {
		inspectCtors();

		return publicCtors.getDefaultCtor();
	}

	/**
	 * Returns the total number of constructors.
	 */
	public int getCtorsCount(boolean declared) {
		inspectCtors();

		Ctors ctors = declared ? allCtors : publicCtors;
		return ctors.getCount();
	}

	/**
	 * Returns an array of all ctors.
	 */
	public Constructor[] getAllCtors(boolean declared) {
		inspectCtors();

		Ctors ctors = declared ? allCtors : publicCtors;
		return ctors.getAllCtors();
	}

}