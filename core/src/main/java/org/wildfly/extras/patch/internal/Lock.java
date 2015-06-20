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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.wildfly.extras.patch.utils.IllegalStateAssertion;


/**
 * The global Lock
 */
final class Lock {
    
    private static final ReentrantLock LOCK = new ReentrantLock();
    
    static void tryLock () {
        try {
            IllegalStateAssertion.assertTrue(LOCK.tryLock(10, TimeUnit.SECONDS), "Cannot obtain lock in time");
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
    static void unlock () {
        LOCK.unlock();
    }
}
