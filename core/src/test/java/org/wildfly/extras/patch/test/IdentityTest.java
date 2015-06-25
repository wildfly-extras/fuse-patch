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
package org.wildfly.extras.patch.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extras.patch.Identity;
import org.wildfly.extras.patch.Version;

public class IdentityTest {

    @Test
    public void testOrdering() throws Exception {

        Identity id1 = Identity.create("aaa", Version.parseVersion("2.0.0"));
        Identity id2 = Identity.create("fuse", Version.parseVersion("1.2.0"));
        Identity id3 = Identity.create("fuse", Version.parseVersion("1.12.0"));
        
        List<Identity> list = Arrays.asList(id3, id2, id1);
        Collections.sort(list);
        
        Assert.assertEquals(id1, list.get(0));
        Assert.assertEquals(id2, list.get(1));
        Assert.assertEquals(id3, list.get(2));
    }
    
    @Test
    public void testFromString() throws Exception {

        Identity id = Identity.fromString("aaa-2.0.0");
        Assert.assertEquals(Identity.create("aaa", Version.parseVersion("2.0.0")), id);
        
        id = Identity.fromString("aaa-2.0.0-SNAPSHOT");
        Assert.assertEquals(Identity.create("aaa", Version.parseVersion("2.0.0-SNAPSHOT")), id);
        Assert.assertEquals("aaa-2.0.0-SNAPSHOT", id.getCanonicalForm());
        
        id = Identity.fromString("aaa-2.0.0.redhat-SNAPSHOT");
        Assert.assertEquals(Identity.create("aaa", Version.parseVersion("2.0.0.redhat-SNAPSHOT")), id);
        Assert.assertEquals("aaa-2.0.0.redhat-SNAPSHOT", id.getCanonicalForm());
        
        id = Identity.fromString("aaa-bbb-ccc");
        Assert.assertEquals(Identity.create("aaa-bbb-ccc", Version.parseVersion("0.0.0")), id);
    }
}
