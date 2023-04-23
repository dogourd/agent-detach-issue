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

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.net.URL;
import java.util.Map;


public class IndyPluginClassLoader extends ByteArrayClassLoader.ChildFirst {

    private static final ClassLoader SYSTEM_CLASS_LOADER = ClassLoader.getSystemClassLoader();

    public IndyPluginClassLoader(ClassLoader targetClassLoader, ClassLoader agentClassLoader, Map<String, byte[]> typeDefinitions) {
        // See getResource on why we're using PersistenceHandler.LATENT over PersistenceHandler.MANIFEST
        super(getParent(targetClassLoader, agentClassLoader),
                true,
                typeDefinitions,
                agentClassLoader.getClass().getProtectionDomain(), // inherit protection domain from agent CL
                PersistenceHandler.LATENT,
                PackageDefinitionStrategy.Trivial.INSTANCE);
    }


    private static ClassLoader getParent(ClassLoader targetClassLoader, ClassLoader agentClassLoader) {
        if (targetClassLoader == null) {
            // the MultipleParentClassLoader doesn't support null values
            // the agent class loader already has the bootstrap class loader as the parent
            return agentClassLoader;
        }

        return new DiscriminatingMultiParentClassLoader(
                agentClassLoader, ElementMatchers.any(),
                targetClassLoader, ElementMatchers.<String>any());

    }

    /**
     * This class loader uses {@link PersistenceHandler#LATENT} (see {@link #IndyPluginClassLoader})
     * as it reduces the memory footprint of the class loader compared to {@link PersistenceHandler#MANIFEST}.
     * With {@link PersistenceHandler#MANIFEST}, after a class has been loaded, the class file byte[] is kept in the typeDefinitions map
     * so that the class can be looked up as a resource.
     * With {@link PersistenceHandler#LATENT}, the class file byte[] is removed from the typeDefinitions after the corresponding class has been loaded.
     * This implies that the class can't be looked up as a resource.
     * The method from the super class even disallows delegation to the parent (as it's a child-first class loader).
     * Overriding this method ensures that we can look up the class resource from the parent class loader (agent class loader).
     */
    @Override
    public URL getResource(String name) {
        URL url = super.getResource(name);
        return url != null
                ? url
                : getParent().getResource(name);
    }

    @Override
    public String toString() {
        return "IndyPluginClassLoader{" +
                " parent=" + getParent() +
                '}';
    }

    public static StartsWithElementMatcher startsWith(String prefix) {
        return new StartsWithElementMatcher(prefix);
    }

    private static class StartsWithElementMatcher extends ElementMatcher.Junction.AbstractBase<String> {

        private final String prefix;

        private StartsWithElementMatcher(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean matches(String s) {
            return s.startsWith(prefix);
        }
    }
}
