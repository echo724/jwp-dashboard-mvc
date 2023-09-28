package di.stage4.annotations;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 스프링의 BeanFactory, ApplicationContext에 해당되는 클래스
 */
class DIContainer {

    private final Set<Object> beans;

    public DIContainer(final Set<Class<?>> classes) {
        beans = instantiateBeans(classes);
    }

    private Set<Object> instantiateBeans(final Set<Class<?>> classes) {
        final Map<Class<?>, Object> classObjectMap = new HashMap<>();
        final List<Class<?>> sortedClasses = sortClassByLessDependnecies(classes);
        for (final Class<?> sortedClass : sortedClasses) {
            if (sortedClass.getConstructors().length != 0) {
                injectConstructorDependency(sortedClass, classObjectMap);
            }
            injectFieldDependency(sortedClass, classObjectMap);
        }
        return new HashSet<>(classObjectMap.values());
    }

    private List<Class<?>> sortClassByLessDependnecies(final Set<Class<?>> classes) {
        return classes.stream()
                .sorted(Comparator
                        .comparing(a -> ((Class<?>) a).getDeclaredFields().length)
                        .reversed()
                )
                .collect(Collectors.toList());
    }

    private void injectFieldDependency(final Class<?> sortedClass, final Map<Class<?>, Object> classObjectMap) {
        Arrays.stream(sortedClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Inject.class))
                .forEach(field -> {
                    try {
                        field.setAccessible(true);
                        final Object instance = classObjectMap.getOrDefault(sortedClass, instantiatePrivateConstructor(sortedClass));
                        field.set(instance, classObjectMap.get(field.getType()));
                        classObjectMap.put(sortedClass, instance);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Object instantiatePrivateConstructor(final Class<?> sortedClass) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        final Constructor<?> declaredConstructor = sortedClass.getDeclaredConstructors()[0];
        declaredConstructor.setAccessible(true);
        return declaredConstructor.newInstance();
    }

    private void injectConstructorDependency(final Class<?> sortedClass, final Map<Class<?>, Object> classObjectMap) {
        final Constructor<?> defaultConstructor = sortedClass.getConstructors()[0];
        if (defaultConstructor.getParameterCount() != 0) {
            instantiateBeanWithParameters(sortedClass, classObjectMap, defaultConstructor);
        } else {
            instantiateBean(sortedClass, classObjectMap, defaultConstructor);
        }
    }

    private void instantiateBean(final Class<?> sortedClass, final Map<Class<?>, Object> classObjectMap, final Constructor<?> defaultConstructor) {
        try {
            putInterfaceOrClass(sortedClass, classObjectMap, defaultConstructor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void instantiateBeanWithParameters(final Class<?> sortedClass, final Map<Class<?>, Object> classObjectMap, final Constructor<?> defaultConstructor) {
        final Class<?>[] parameterTypes = defaultConstructor.getParameterTypes();
        final Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameters[i] = classObjectMap.get(parameterTypes[i]);
        }
        try {
            putInterfaceOrClass(sortedClass, classObjectMap, defaultConstructor, parameters);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void putInterfaceOrClass(final Class<?> sortedClass, final Map<Class<?>, Object> classObjectMap, final Constructor<?> constructor, final Object... parameters) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        final Class<?>[] interfaces = sortedClass.getInterfaces();
        for (final Class<?> anInterface : interfaces) {
            classObjectMap.put(anInterface, constructor.newInstance(parameters));
        }
        classObjectMap.put(sortedClass, constructor.newInstance(parameters));
    }

    public static DIContainer createContainerForPackage(final String packageName) {
        return new DIContainer(ClassPathScanner.getAllClassesInPackage(packageName));
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(final Class<T> aClass) {
        System.out.println("beans = " + beans);
        return (T) beans.stream()
                .peek(System.out::println)
                .filter(bean -> aClass.isAssignableFrom(bean.getClass()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("해당하는 클래스가 존재하지 않습니다."));
    }
}
