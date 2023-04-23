/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.example;


import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;

public class IndyPluginClassLoaderFactory {


    private static final Map<ClassLoader, Map<Collection<String>, WeakReference<ClassLoader>>> alreadyInjected
            = new WeakHashMap<ClassLoader, Map<Collection<String>, WeakReference<ClassLoader>>>();


    public synchronized static ClassLoader getOrCreatePluginClassLoader(ClassLoader targetClassLoader,
                                                                        List<String> classesToInject,
                                                                        ClassLoader agentClassLoader,
                                                                        ClassFileLocator classFileLocator,
                                                                        ElementMatcher<? super TypeDescription> exclusionMatcher) throws Exception {
        classesToInject = new ArrayList<>(classesToInject);

        Map<Collection<String>, WeakReference<ClassLoader>> injectedClasses = getOrCreateInjectedClasses(targetClassLoader);
        if (injectedClasses.containsKey(classesToInject)) {
            ClassLoader pluginClassLoader = injectedClasses.get(classesToInject).get();
            if (pluginClassLoader == null) {
                injectedClasses.remove(classesToInject);
            } else {
                return pluginClassLoader;
            }
        }

        List<String> classesToInjectCopy = new ArrayList<>(classesToInject.size());
        TypePool pool = new TypePool.Default.WithLazyResolution(TypePool.CacheProvider.NoOp.INSTANCE, classFileLocator, TypePool.Default.ReaderMode.FAST);
        for (Iterator<String> iterator = classesToInject.iterator(); iterator.hasNext(); ) {
            String className = iterator.next();
            boolean excluded;
            try {
                excluded = exclusionMatcher.matches(pool.describe(className).resolve());
            } catch (Exception e) {
                // in case a matcher fails, for example because it can't resolve a type description
                excluded = false;
            }
            if (!excluded) {
                classesToInjectCopy.add(className);
            }
        }
        System.out.printf("Creating plugin class loader for %s containing %s\n.", targetClassLoader, classesToInjectCopy);

        Map<String, byte[]> typeDefinitions = getTypeDefinitions(classesToInjectCopy, classFileLocator);
        // child first semantics are important here as the plugin CL contains classes that are also present in the agent CL
        ClassLoader pluginClassLoader = new IndyPluginClassLoader(targetClassLoader, agentClassLoader, typeDefinitions);
        injectedClasses.put(classesToInject, new WeakReference<>(pluginClassLoader));

        return pluginClassLoader;
    }

    private static Map<Collection<String>, WeakReference<ClassLoader>> getOrCreateInjectedClasses(ClassLoader targetClassLoader) {
        Map<Collection<String>, WeakReference<ClassLoader>> injectedClasses = alreadyInjected.get(targetClassLoader);
        if (injectedClasses == null) {
            injectedClasses = new HashMap<>();
            alreadyInjected.put(targetClassLoader, injectedClasses);
        }
        return injectedClasses;
    }

    public synchronized static void clear() {
        alreadyInjected.clear();
    }

    private static Map<String, byte[]> getTypeDefinitions(List<String> helperClassNames, ClassFileLocator classFileLocator) throws IOException {
        Map<String, byte[]> typeDefinitions = new HashMap<>();
        for (final String helperName : helperClassNames) {
            final byte[] classBytes = classFileLocator.locate(helperName).resolve();
            typeDefinitions.put(helperName, classBytes);
        }
        return typeDefinitions;
    }

}
