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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.redhat.fuse.patch.PatchId;
import com.redhat.fuse.patch.Version;

public class PatchIdTest {

    @Test
    public void testOrdering() throws Exception {

        PatchId id1 = PatchId.create("aaa", Version.parseVersion("2.0.0"));
        PatchId id2 = PatchId.create("fuse", Version.parseVersion("1.2.0"));
        PatchId id3 = PatchId.create("fuse", Version.parseVersion("1.12.0"));
        
        List<PatchId> list = Arrays.asList(id3, id2, id1);
        Collections.sort(list);
        
        Assert.assertEquals(id1, list.get(0));
        Assert.assertEquals(id2, list.get(1));
        Assert.assertEquals(id3, list.get(2));
    }
}
