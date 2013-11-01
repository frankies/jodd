// Copyright (c) 2003-2013, Jodd Team (jodd.org). All Rights Reserved.

package jodd.petite;

import jodd.introspector.ClassDescriptor;
import jodd.introspector.ClassIntrospector;
import jodd.introspector.FieldDescriptor;
import jodd.petite.meta.InitMethodInvocationStrategy;
import jodd.petite.scope.DefaultScope;
import jodd.petite.scope.Scope;
import jodd.props.Props;
import jodd.util.ReflectUtil;
import jodd.util.StringPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base layer of {@link PetiteContainer Petite Container}.
 * Holds beans and scopes definitions.
 */
public abstract class PetiteBeans {

	private static final Logger log = LoggerFactory.getLogger(PetiteBeans.class);

	/**
	 * Map of all beans definitions.
	 */
	protected final Map<String, BeanDefinition> beans = new HashMap<String, BeanDefinition>();

	/**
	 * Map of all bean scopes.
	 */
	protected final Map<Class<? extends Scope>, Scope> scopes = new HashMap<Class<? extends Scope>, Scope>();

	/**
	 * Map of all providers.
	 */
	protected final Map<String, ProviderDefinition> providers = new HashMap<String, ProviderDefinition>();

	/**
	 * Map of all bean collections.
	 */
	protected final Map<Class, String[]> beanCollections = new HashMap<Class, String[]>();

	/**
	 * {@link PetiteConfig Petite configuration}.
	 */
	protected final PetiteConfig petiteConfig;

	/**
	 * {@link InjectionPointFactory Injection point factory}.
	 */
	protected final InjectionPointFactory injectionPointFactory;

	/**
	 * {@link PetiteResolvers Petite resolvers}.
	 */
	protected final PetiteResolvers petiteResolvers;

	/**
	 * {@link ParamManager Parameters manager}.
	 */
	protected final ParamManager paramManager;

	protected PetiteBeans(PetiteConfig petiteConfig) {
		this.petiteConfig = petiteConfig;
		this.injectionPointFactory = new InjectionPointFactory(petiteConfig);
		this.petiteResolvers = new PetiteResolvers(injectionPointFactory);
		this.paramManager = new ParamManager();
	}

	/**
	 * Returns parameter manager.
	 */
	public ParamManager getParamManager() {
		return paramManager;
	}

	/**
	 * Returns {@link PetiteConfig Petite configuration}.
	 * All changes on config should be done <b>before</b>
	 * beans registration process starts.
	 */
	public PetiteConfig getConfig() {
		return petiteConfig;
	}

	// ---------------------------------------------------------------- scopes

	/**
	 * Resolves and registers scope from a scope type.
	 */
	@SuppressWarnings("unchecked")
	public <S extends Scope> S resolveScope(Class<S> scopeType) {
		S scope = (S) scopes.get(scopeType);
		if (scope == null) {

			try {
				scope = PetiteUtil.newInstance(scopeType, (PetiteContainer) this);
			} catch (Exception ex) {
				throw new PetiteException("Unable to create Petite scope: " + scopeType.getName(), ex);
			}

			registerScope(scopeType, scope);
			scopes.put(scopeType, scope);
		}
		return scope;
	}

	/**
	 * Registers new scope. It is not necessary to manually register scopes,
	 * since they become registered on first scope resolving.
	 * However, it is possible to pre-register some scopes, or to <i>replace</i> one scope
	 * type with another. Replacing may be important for testing purposes when
	 * using container-depended scopes.
	 */
	public void registerScope(Class<? extends Scope> scopeType, Scope scope) {
		scopes.put(scopeType, scope);
	}

	// ---------------------------------------------------------------- lookup beans

	/**
	 * Lookups for {@link BeanDefinition bean definition}.
	 * Returns <code>null</code> if bean name doesn't exist.
	 */
	public BeanDefinition lookupBeanDefinition(String name) {
		return beans.get(name);
	}

	/**
	 * Lookups for first founded {@link BeanDefinition bean definition}.
	 */
	protected BeanDefinition lookupBeanDefinitions(String... names) {
		for (String name : names) {
			BeanDefinition beanDefinition = lookupBeanDefinition(name);
			if (beanDefinition != null) {
				return beanDefinition;
			}
		}
		return null;
	}

	/**
	 * Lookups for existing bean. Throws exception if bean is not found.
	 */
	protected BeanDefinition lookupExistingBeanDefinition(String name) {
		BeanDefinition beanDefinition = lookupBeanDefinition(name);
		if (beanDefinition == null) {
			throw new PetiteException("Bean not found: " + name);
		}
		return beanDefinition;
	}

	/**
	 * Returns <code>true</code> if bean name is registered.
	 */
	public boolean isBeanNameRegistered(String name) {
		return lookupBeanDefinition(name) != null;
	}

	/**
	 * Resolves bean's name from bean annotation or type name. May be used for resolving bean name
	 * of base type during registration of bean subclass.
	 */
	public String resolveBeanName(Class type) {
		return PetiteUtil.resolveBeanName(type, petiteConfig.getUseFullTypeNames());
	}

	// ---------------------------------------------------------------- register beans

	/**
	 * Registers or defines a bean.
	 *
	 * @param type bean type, must be specified
	 * @param name bean name, if <code>null</code> it will be resolved from the class (name or annotation)
	 * @param scopeType bean scope, if <code>null</code> it will be resolved from the class (annotation or default one)
	 * @param wiringMode wiring mode, if <code>null</code> it will be resolved from the class (annotation or default one)
	 * @param define when set to <code>true</code> bean will be defined - all injection points will be set to none
	 */
	public BeanDefinition registerPetiteBean(
			Class type, String name,
			Class<? extends Scope> scopeType,
			WiringMode wiringMode,
			boolean define) {

		if (name == null) {
			name = PetiteUtil.resolveBeanName(type, petiteConfig.getUseFullTypeNames());
		}
		if (wiringMode == null) {
			wiringMode = PetiteUtil.resolveBeanWiringMode(type);
		}
		if (wiringMode == WiringMode.DEFAULT) {
			wiringMode = petiteConfig.getDefaultWiringMode();
		}
		if (scopeType == null) {
			scopeType = PetiteUtil.resolveBeanScopeType(type);
		}
		if (scopeType == DefaultScope.class) {
			scopeType = petiteConfig.getDefaultScope();
		}
		BeanDefinition existing = removeBean(name);
		if (existing != null) {
			if (petiteConfig.getDetectDuplicatedBeanNames()) {
				throw new PetiteException(
						"Duplicated bean name detected while registering class '" + type.getName() + "'. Petite bean class '" +
						existing.type.getName() + "' is already registered with the name: " + name);
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("Registering bean: " + name +
					" of type: " + type.getSimpleName() +
					" in: " + scopeType.getSimpleName() +
					" using wiring mode: " + wiringMode.toString());
		}

		// registering

		// check if type is valid
		if ((type != null) && (type.isInterface() == true)) {
			throw new PetiteException("Unable to register interface: " + type.getName());
		}

		// register
		Scope scope = resolveScope(scopeType);
		BeanDefinition beanDefinition = new BeanDefinition(name, type, scope, wiringMode);
		beans.put(name, beanDefinition);

		// providers
		ProviderDefinition[] providerDefinitions = petiteResolvers.resolveProviderDefinitions(beanDefinition);

		if (providerDefinitions != null) {
			for (ProviderDefinition providerDefinition : providerDefinitions) {
				providers.put(providerDefinition.name, providerDefinition);
			}
		}

		// define
		if (define) {
			beanDefinition.ctor = petiteResolvers.resolveCtorInjectionPoint(type);
			beanDefinition.properties = PropertyInjectionPoint.EMPTY;
			beanDefinition.methods = MethodInjectionPoint.EMPTY;
			beanDefinition.initMethods = InitMethodPoint.EMPTY;
		}

		// return
		return beanDefinition;
	}

	/**
	 * Removes all petite beans of provided type. Bean name is not resolved from a type!
	 * Instead, all beans are iterated and only beans with equal types are removed.
	 * @see #removeBean(String)
	 */
	public void removeBean(Class type) {
		// collect bean names
		Set<String> beanNames = new HashSet<String>();

		for (BeanDefinition def : beans.values()) {
			if (def.type.equals(type)) {
				beanNames.add(def.name);
			}
		}

		// remove collected bean names
		for (String beanName : beanNames) {
			removeBean(beanName);
		}
	}

	/**
	 * Removes bean and returns definition of removed bean.
	 * All resolvers references are deleted, too.
	 * Returns bean definition of removed bean or <code>null</code>.
	 */
	public BeanDefinition removeBean(String name) {
		BeanDefinition bd = beans.remove(name);
		if (bd == null) {
			return null;
		}
		bd.scopeRemove();
		return bd;
	}

	// ---------------------------------------------------------------- bean collections

	/**
	 * Resolves bean names for give type.
	 */
	protected String[] resolveBeanNamesForType(Class type) {
		String[] beanNames = beanCollections.get(type);
		if (beanNames != null) {
			return beanNames;
		}

		ArrayList<String> list = new ArrayList<String>();

		for (Map.Entry<String, BeanDefinition> entry : beans.entrySet()) {
			BeanDefinition beanDefinition = entry.getValue();

			if (ReflectUtil.isSubclass(beanDefinition.type, type)) {
				String beanName = entry.getKey();
				list.add(beanName);
			}
		}

		if (list.isEmpty()) {
			beanNames = StringPool.EMPTY_ARRAY;
		} else {
			beanNames = list.toArray(new String[list.size()]);
		}

		beanCollections.put(type, beanNames);
		return beanNames;
	}

	// ---------------------------------------------------------------- injection points

	/**
	 * Registers constructor injection point.
	 *
	 * @param beanName bean name
	 * @param paramTypes constructor parameter types, may be <code>null</code>
	 * @param references references for arguments
	 */
	public void registerPetiteCtorInjectionPoint(String beanName, Class[] paramTypes, String[] references) {
		BeanDefinition beanDefinition = lookupExistingBeanDefinition(beanName);
		String[][] ref = PetiteUtil.convertRefToReferences(references);

		ClassDescriptor cd = ClassIntrospector.lookup(beanDefinition.type);
		Constructor constructor = null;

		if (paramTypes == null) {
			Constructor[] ctors = cd.getAllCtors(true);
			if (ctors != null && ctors.length > 0) {
				if (ctors.length > 1) {
					throw new PetiteException(ctors.length + " suitable constructor found as injection point for: " + beanDefinition.type.getName());
				}
				constructor = ctors[0];
			}
		} else {
			constructor = cd.getCtor(paramTypes, true);
		}

		if (constructor == null) {
			throw new PetiteException("Constructor not found: " + beanDefinition.type.getName());
		}

		beanDefinition.ctor = injectionPointFactory.createCtorInjectionPoint(constructor, ref);
	}

	/**
	 * Registers property injection point.
	 *
	 * @param beanName bean name
	 * @param property property name
	 * @param reference explicit injection reference, may be <code>null</code>
	 */
	public void registerPetitePropertyInjectionPoint(String beanName, String property, String reference) {
		BeanDefinition beanDefinition = lookupExistingBeanDefinition(beanName);
		String[] references = reference == null ? null : new String[] {reference};

		ClassDescriptor cd = ClassIntrospector.lookup(beanDefinition.type);
		FieldDescriptor fieldDescriptor = cd.getFieldDescriptor(property, true);
		if (fieldDescriptor == null) {
			throw new PetiteException("Property not found: " + beanDefinition.type.getName() + '#' + property);
		}

		PropertyInjectionPoint pip =
				injectionPointFactory.createPropertyInjectionPoint(fieldDescriptor.getField(), references);

		beanDefinition.addPropertyInjectionPoint(pip);
	}

	/**
	 * Registers set injection point.
	 *
	 * @param beanName bean name
	 * @param property set property name
	 */
	public void registerPetiteSetInjectionPoint(String beanName, String property) {
		BeanDefinition beanDefinition = lookupExistingBeanDefinition(beanName);
		ClassDescriptor cd = ClassIntrospector.lookup(beanDefinition.type);
		FieldDescriptor fieldDescriptor = cd.getFieldDescriptor(property, true);
		if (fieldDescriptor == null) {
			throw new PetiteException("Property not found: " + beanDefinition.type.getName() + '#' + property);
		}

		SetInjectionPoint sip =
				injectionPointFactory.createSetInjectionPoint(fieldDescriptor.getField());

		beanDefinition.addSetInjectionPoint(sip);
	}

	/**
	 * Registers method injection point.
	 *
	 * @param beanName bean name
	 * @param methodName method name
	 * @param arguments method arguments, may be <code>null</code>
	 * @param references injection references
	 */
	public void registerPetiteMethodInjectionPoint(String beanName, String methodName, Class[] arguments, String[] references) {
		BeanDefinition beanDefinition = lookupExistingBeanDefinition(beanName);
		String[][] ref = PetiteUtil.convertRefToReferences(references);
		ClassDescriptor cd = ClassIntrospector.lookup(beanDefinition.type);

		Method method = null;
		if (arguments == null) {
			Method[] methods = cd.getAllMethods(methodName, true);
			if (methods != null && methods.length > 0) {
				if (methods.length > 1) {
					throw new PetiteException(methods.length + " suitable methods found as injection points for: " + beanDefinition.type.getName() + '#' + methodName);
				}
				method = methods[0];
			}
		} else {
			method = cd.getMethod(methodName, arguments, true);
		}
		if (method == null) {
			throw new PetiteException("Method not found: " + beanDefinition.type.getName() + '#' + methodName);
		}
		MethodInjectionPoint mip = injectionPointFactory.createMethodInjectionPoint(method, ref);

		beanDefinition.addMethodInjectionPoint(mip);
	}

	/**
	 * Registers init method.
	 *
	 * @param beanName bean name
	 * @param invocationStrategy moment of invocation
	 * @param initMethodNames init method names
	 */
	public void registerPetiteInitMethods(String beanName, InitMethodInvocationStrategy invocationStrategy, String... initMethodNames) {
		BeanDefinition beanDefinition = lookupExistingBeanDefinition(beanName);

		ClassDescriptor cd = ClassIntrospector.lookup(beanDefinition.type);
		if (initMethodNames == null) {
			initMethodNames = StringPool.EMPTY_ARRAY;
		}

		int total = initMethodNames.length;
		InitMethodPoint[] initMethodPoints = new InitMethodPoint[total];

		int i;
		for (i = 0; i < initMethodNames.length; i++) {
			Method m = cd.getMethod(initMethodNames[i], ReflectUtil.NO_PARAMETERS, true);
			if (m == null) {
				throw new PetiteException("Init method not found: " + beanDefinition.type.getName() + '#' + initMethodNames[i]);
			}
			initMethodPoints[i] = new InitMethodPoint(m, i, invocationStrategy);
		}

		beanDefinition.addInitMethodPoints(initMethodPoints);
	}

	// ---------------------------------------------------------------- providers

	/**
	 * Registers instance method provider.
	 *
	 * @param providerName provider name
	 * @param beanName bean name
	 * @param methodName instance method name
	 * @param arguments method argument types, may be <code>null</code>
	 */
	public void registerPetiteProvider(String providerName, String beanName, String methodName, Class[] arguments) {
		BeanDefinition beanDefinition = lookupBeanDefinition(beanName);

		if (beanDefinition == null) {
			throw new PetiteException("Bean not found: " + beanName);
		}

		Class beanType = beanDefinition.type;

		ClassDescriptor cd = ClassIntrospector.lookup(beanType);
		Method method = cd.getMethod(methodName, arguments, true);

		if (method == null) {
			throw new PetiteException("Provider method not found: " + methodName);
		}

		ProviderDefinition providerDefinition = new ProviderDefinition(providerName, beanName, method);

		providers.put(providerName, providerDefinition);
	}

	/**
	 * Registers static method provider.
	 *
	 * @param providerName provider name
	 * @param type class type
	 * @param staticMethodName static method name
	 * @param arguments method argument types, may be <code>null</code>
	 */
	public void registerPetiteProvider(String providerName, Class type, String staticMethodName, Class[] arguments) {
		ClassDescriptor cd = ClassIntrospector.lookup(type);
		Method method = cd.getMethod(staticMethodName, arguments, true);

		if (method == null) {
			throw new PetiteException("Provider method not found: " + staticMethodName);
		}

		ProviderDefinition providerDefinition = new ProviderDefinition(providerName, method);

		providers.put(providerName, providerDefinition);
	}

	// ---------------------------------------------------------------- statistics

	/**
	 * Returns total number of registered beans.
	 */
	public int getTotalBeans() {
		return beans.size();
	}

	/**
	 * Returns total number of used scopes.
	 */
	public int getTotalScopes() {
		return scopes.size();
	}

	/**
	 * Returns set of all bean names.
	 */
	public Set<String> getBeanNames() {
		return beans.keySet();
	}

	// ---------------------------------------------------------------- params

	/**
	 * Defines new parameter. Parameters with same name will be replaced.
	 */
	public void defineParameter(String name, Object value) {
		paramManager.put(name, value);
	}

	/**
	 * Returns defined parameter.
	 */
	public Object getParameter(String name) {
		return paramManager.get(name);
	}

	/**
	 * Prepares list of all bean parameters and optionally resolves inner references.
	 */
	protected String[] resolveBeanParams(String name, boolean resolveReferenceParams) {
		return paramManager.resolve(name, resolveReferenceParams);
	}

	/**
	 * Defines many parameters at once.
	 */
	public void defineParameters(Map<?, ?> properties) {
		for (Map.Entry<?, ?> entry : properties.entrySet()) {
			defineParameter(entry.getKey().toString(), entry.getValue());
		}
	}

	/**
	 * Defines many parameters at once from {@link jodd.props.Props}.
	 */
	public void defineParameters(Props props) {
		Map<?, ?> map = new HashMap<Object, Object>();
		props.extractProps(map);
		defineParameters(map);
	}

}