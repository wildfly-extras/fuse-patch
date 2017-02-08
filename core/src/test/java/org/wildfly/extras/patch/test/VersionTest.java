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

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extras.patch.Version;

public class VersionTest {

    @Test
    public void testDotQualifier() throws Exception {

        Assert.assertEquals("1.0.0", Version.parseVersion("1.0.0").toString());
        Assert.assertEquals("1.0.0-SNAPSHOT", Version.parseVersion("1.0.0-SNAPSHOT").toString());
        Assert.assertEquals("1.0.0-SNAPSHOT", Version.parseVersion("1.0-SNAPSHOT").toString());
        Assert.assertEquals("1.0.0-SNAPSHOT", Version.parseVersion("1-SNAPSHOT").toString());
        Assert.assertEquals("1.0.0.SP1", Version.parseVersion("1.0.0.SP1").toString());
        Assert.assertEquals("SP1", Version.parseVersion("1.0.0.SP1").getQualifier());
        Assert.assertEquals("1.0.0.SP1-redhat-1", Version.parseVersion("1.0.0.SP1-redhat-1").toString());
        Assert.assertEquals("SP1-redhat-1", Version.parseVersion("1.0.0.SP1-redhat-1").getQualifier());
        Assert.assertEquals("1.0.0.SP1-SNAPSHOT", Version.parseVersion("1.0.0.SP1-SNAPSHOT").toString());
        Assert.assertEquals("SP1-SNAPSHOT", Version.parseVersion("1.0.0.SP1-SNAPSHOT").getQualifier());

        Assert.assertEquals(-1, Version.parseVersion("1.0.0.SP1-redhat-1").compareTo(Version.parseVersion("1.0.0.SP2-redhat-1")));
    }
    
    @Test
    public void testDashQualifier() throws Exception {

        Assert.assertEquals("1.0.0", Version.parseVersion("1.0.0").toString());
        Assert.assertEquals("1.0.0-SNAPSHOT", Version.parseVersion("1.0.0-SNAPSHOT").toString());
        Assert.assertEquals("1.0.0-SNAPSHOT", Version.parseVersion("1.0-SNAPSHOT").toString());
        Assert.assertEquals("1.0.0-SNAPSHOT", Version.parseVersion("1-SNAPSHOT").toString());
        Assert.assertEquals("1.0.0-SP1", Version.parseVersion("1.0.0-SP1").toString());
        Assert.assertEquals("SP1", Version.parseVersion("1.0.0-SP1").getQualifier());
        Assert.assertEquals("1.0.0-SP1-redhat-1", Version.parseVersion("1.0.0-SP1-redhat-1").toString());
        Assert.assertEquals("SP1-redhat-1", Version.parseVersion("1.0.0-SP1-redhat-1").getQualifier());
        Assert.assertEquals("1.0.0-SP1-SNAPSHOT", Version.parseVersion("1.0.0-SP1-SNAPSHOT").toString());
        Assert.assertEquals("SP1-SNAPSHOT", Version.parseVersion("1.0.0-SP1-SNAPSHOT").getQualifier());
        Assert.assertEquals("2.7.0-4.fuse-000001", Version.parseVersion("2.7.0-4.fuse-000001").toString());
        Assert.assertEquals("4.fuse-000001", Version.parseVersion("2.7.0-4.fuse-000001").getQualifier());

        Assert.assertEquals(-1, Version.parseVersion("1.0.0-SP1-redhat-1").compareTo(Version.parseVersion("1.0.0-SP2-redhat-1")));
        Assert.assertEquals(-1, Version.parseVersion("2.7.0-4.fuse-000001").compareTo(Version.parseVersion("2.7.0-5.fuse-000001")));
    }
}
