/*
 * #%L
 * Fuse Patch :: Parser
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
package com.redhat.fuse.patch.test;

import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import com.redhat.fuse.patch.SmartPatch.Metadata;
import com.redhat.fuse.patch.internal.DefaultPatchRepository;

public class RepositoryMetadataTest {

    @Test
    public void testRelativeRepositoryUrl() throws Exception {
        DefaultPatchRepository repo = new DefaultPatchRepository(Paths.get("target").toUri().toURL());
        Metadata metadata = repo.parseMetadata(Paths.get("src/test/resources/A1.repo.metadata"));
        Assert.assertEquals(1, metadata.getPostCommands().size());
        Assert.assertEquals("bin/fusepatch.sh --query-server", metadata.getPostCommands().get(0));
    }
}
