/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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
package org.mvndaemon.mvnd.common;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MavenDaemon {

    public static void main(String[] args) throws Exception {
        final Path mvndHome = Environment.MVND_HOME.asPath();
        URL[] classpath = Stream.concat(
                        /* jars */
                        Stream.of("mvn/lib/ext", "mvn/lib", "mvn/boot")
                                .map(mvndHome::resolve)
                                .flatMap((Path p) -> {
                                    try {
                                        return Files.list(p);
                                    } catch (java.io.IOException e) {
                                        throw new RuntimeException("Could not list " + p, e);
                                    }
                                })
                                .filter(p -> {
                                    final String fileName = p.getFileName().toString();
                                    return fileName.endsWith(".jar") && !fileName.startsWith("mvnd-client-");
                                })
                                .filter(Files::isRegularFile),
                        /* resources */
                        Stream.of(mvndHome.resolve("mvn/conf"), mvndHome.resolve("mvn/conf/logging")))
                .map(Path::normalize)
                .map(Path::toUri)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new);
        ClassLoader loader = new URLClassLoader(classpath, null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                try {
                    return super.findClass(name);
                } catch (ClassNotFoundException e) {
                    return MavenDaemon.class.getClassLoader().loadClass(name);
                }
            }

            @Override
            public URL getResource(String name) {
                URL url = super.getResource(name);
                if (url == null) {
                    url = MavenDaemon.class.getClassLoader().getResource(name);
                }
                return url;
            }
        };
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> clazz = loader.loadClass("org.mvndaemon.mvnd.daemon.Server");
        try (AutoCloseable server = (AutoCloseable) clazz.getConstructor().newInstance()) {
            ((Runnable) server).run();
        }
    }
}
