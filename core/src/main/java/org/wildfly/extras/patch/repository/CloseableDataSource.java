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
package org.wildfly.extras.patch.repository;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

public abstract class CloseableDataSource implements DataSource, Closeable {

    private final DataSource delegate;
    
    public CloseableDataSource(DataSource delegate) {
        IllegalArgumentAssertion.assertNotNull(delegate, "delegate");
        this.delegate = delegate;
    }
    
    public String getContentType() {
        return delegate.getContentType();
    }

    public InputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    public String getName() {
        return delegate.getName();
    }

    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }
}
