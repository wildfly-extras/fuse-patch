/*
 * #%L
 * Fuse Patch :: Core
 * %%
 * Copyright (C) 2015 Private
 * %%
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
 * #L%
 */
package org.wildfly.extras.patch.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.extras.patch.PatchException;


final class PatchLogger {
    
    private static final Logger LOG = LoggerFactory.getLogger("org.wildfly.extras.patch");
    
    static void info (String message) {
        System.out.println(message);
        LOG.info(message);
    }

    static void warn(String message) {
        System.err.println("Warning: " + message);
        LOG.warn(message);
    }
    
    static void error(String message) {
        System.err.println("Error: " + message);
        LOG.error(message);
    }

    static void error(PatchException ex) {
        System.err.println("Error: " + ex.getMessage());
        LOG.error("", ex);
    }
}
