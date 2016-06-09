/*
 * #%L
 * Fuse Patch :: Integration Tests :: Standalone
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.gravia.resource.ManifestBuilder;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.patch.Patch;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchTool;
import org.wildfly.extras.patch.PatchToolBuilder;
import org.wildfly.extras.patch.Repository;
import org.wildfly.extras.patch.repository.LocalFileRepository;
import org.wildfly.extras.patch.test.subA.ClassA;
import org.wildfly.extras.patch.utils.IOUtils;

@RunWith(Arquillian.class)
public class RepositoryEndpointTest {

	@ArquillianResource
	private Deployer deployer;

    @Deployment
    public static JavaArchive deployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "repository-endpoint-test.jar");
        archive.addAsResource("fusepatch.configuration");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                ManifestBuilder builder = new ManifestBuilder();
                builder.addManifestHeader("Dependencies", "org.wildfly.extras.patch");
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testRepository() throws Exception {

    	// Configure JAX-WS Client
        ClassLoader classLoader = getClass().getClassLoader();
        URL configUrl = classLoader.getResource("/fusepatch.configuration");
        PatchTool patchTool = new PatchToolBuilder().loadConfiguration(configUrl).build();

        Repository repository = patchTool.getRepository();

        // Test repository base URL
        URL baseURL = repository.getRepositoryURL();
        Assert.assertEquals(new URL("http://localhost:8080/fuse-patch-jaxws/RepositoryEndpoint"), baseURL);

        PatchId pid = PatchId.fromString("fuse-patch-distro-wildfly-" + PatchTool.VERSION);
        List<PatchId> pids = repository.queryAvailable(null);

        // With the feature pack runtime we need to install the fuse-patch package
        if (pids.size() == 0) {
            URL fileUrl = new URL(System.getProperty(Repository.SYSTEM_PROPERTY_REPOSITORY_URL) + "/" + pid + ".zip");
            Assert.assertEquals(pid, repository.addArchive(fileUrl));
        }

        // Verify queryAvailable
        pids = repository.queryAvailable(null);
        Assert.assertEquals(1, pids.size());
        Assert.assertEquals(pid, pids.get(0));

        // Verify getLatestAvailable
        Assert.assertEquals(pid, repository.getLatestAvailable("fuse-patch-distro-wildfly"));

        // Add foo-1.0.0
        URL fileUrl = getArchiveURL("foo-1.0.0");
        PatchId pidFoo100 = repository.addArchive(fileUrl);
        Patch packFoo100 = repository.getPatch(pidFoo100);
        Assert.assertEquals(pidFoo100, packFoo100.getPatchId());
        Assert.assertEquals(4, packFoo100.getRecords().size());
        Assert.assertEquals(0, packFoo100.getMetadata().getPostCommands().size());

        // Remove foo-1.0.0
        Assert.assertTrue(repository.removeArchive(pidFoo100));
        Assert.assertNull(repository.getLatestAvailable("foo"));

        // Add foo-1.0.0
        fileUrl = getArchiveURL("foo-1.0.0");
        PatchMetadata mdFoo100 = new PatchMetadataBuilder().patchId(pidFoo100).roles("FooRole").build();
        DataHandler dataFoo100 = new DataHandler(new URLDataSource(fileUrl));
        Assert.assertEquals(pidFoo100, repository.addArchive(mdFoo100, dataFoo100, false));
        packFoo100 = repository.getPatch(pidFoo100);
        Assert.assertEquals(pidFoo100, packFoo100.getPatchId());
        Assert.assertEquals(4, packFoo100.getRecords().size());
        Assert.assertEquals(1, packFoo100.getMetadata().getRoles().size());
        Assert.assertEquals(0, packFoo100.getMetadata().getPostCommands().size());

        // Add foo-1.1.0
        fileUrl = getArchiveURL("foo-1.1.0");
        PatchId pidFoo110 = PatchId.fromURL(fileUrl);
        String cmd = getPostCommand("echo hello world");
        PatchMetadata mdFoo110 = new PatchMetadataBuilder().patchId(pidFoo110).roles("FooRole").postCommands(cmd).build();
        DataHandler dataFoo110 = new DataHandler(new URLDataSource(fileUrl));
        Assert.assertEquals(pidFoo110, repository.addArchive(mdFoo110, dataFoo110, false));
        Patch packFoo110 = repository.getPatch(pidFoo110);
        Assert.assertEquals(pidFoo110, packFoo110.getPatchId());
        Assert.assertEquals(3, packFoo110.getRecords().size());
        Assert.assertEquals(1, packFoo110.getMetadata().getRoles().size());
        Assert.assertEquals(1, packFoo110.getMetadata().getPostCommands().size());

        // Install foo-1.0.0
        packFoo100 = patchTool.install(pidFoo100, false);
        Assert.assertEquals(pidFoo100, packFoo100.getPatchId());
        Assert.assertEquals(4, packFoo100.getRecords().size());
        Assert.assertEquals(0, packFoo100.getMetadata().getPostCommands().size());

        // Update foo
        packFoo110 = patchTool.update("foo", false);
        Assert.assertEquals(pidFoo110, packFoo110.getPatchId());
        Assert.assertEquals(3, packFoo110.getRecords().size());
        Assert.assertEquals(1, packFoo110.getMetadata().getPostCommands().size());

        // Add bar-1.0.0
        fileUrl = getArchiveURL("bar-1.0.0");
        PatchId pidBar100 = PatchId.fromURL(fileUrl);
        PatchMetadata mdBar100 = new PatchMetadataBuilder().patchId(pidBar100).roles("BarRole").build();
        DataHandler dataBar100 = new DataHandler(new URLDataSource(fileUrl));
        Assert.assertEquals(pidBar100, repository.addArchive(mdBar100, dataBar100, false));
        Patch packBar100 = repository.getPatch(pidBar100);
        Assert.assertEquals(pidBar100, packBar100.getPatchId());
        Assert.assertEquals(2, packBar100.getRecords().size());
        Assert.assertEquals(1, packBar100.getMetadata().getRoles().size());

        // Install bar-1.0.0
        try {
            patchTool.install(pidBar100, false);
            Assert.fail("SecurityException expected");
        } catch (SecurityException ex) {
            Assert.assertTrue(ex.getMessage().contains("User does not have required role: BarRole"));
        }
    }

    private String getPostCommand(String cmd) {
    	if (LocalFileRepository.isWindows()) {
    		cmd = "cmd /c " + cmd;
    	}
		return cmd;
	}

	private URL getArchiveURL(String name) throws IOException {
    	File dataDir = new File(System.getProperty("jboss.server.data.dir"));
    	File patchDir = new File(dataDir, "fusepatch");
    	patchDir.mkdirs();
    	File patchFile = new File(patchDir, name + ".zip");
    	if (!patchFile.isFile()) {
    	    InputStream input = deployer.getDeployment(name);
        	try {
        		IOUtils.copy(input, new FileOutputStream(patchFile));
        	} finally {
        	    input.close();
        	}
    	}
		return patchFile.toURI().toURL();
	}

	/*
     * foo-1.0.0.zip
     *
     * config/remove-me.properties
     * config/propsA.properties
     * config/propsB.properties
     * lib/foo-1.0.0.jar
     */
    @Deployment(name = "foo-1.0.0", managed = false, testable = false)
    public static GenericArchive foo100() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.0.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/archives/propsA1.properties")), "config/remove-me.properties");
        archive.add(new FileAsset(new File("src/test/resources/archives/propsA1.properties")), "config/propsA.properties");
        archive.add(new FileAsset(new File("src/test/resources/archives/propsB.properties")), "config/propsB.properties");
        return archive;
    }

    /*
     * foo-1.1.0.zip
     *
     * config/propsA.properties
     * config/propsB.properties
     * lib/foo-1.1.0.jar
     */
    @Deployment(name = "foo-1.1.0", managed = false, testable = false)
    public static GenericArchive foo110() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "foo-1.1.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/archives/propsA2.properties")), "config/propsA.properties");
        archive.add(new FileAsset(new File("src/test/resources/archives/propsB.properties")), "config/propsB.properties");
        return archive;
    }

    /*
     * bar-1.0.0.zip
     *
     * config/propsB.properties
     * lib/bar-1.0.0.jar
     */
    @Deployment(name = "bar-1.0.0", managed = false, testable = false)
    public static GenericArchive bar100() throws IOException {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "bar-1.0.0.jar");
        jar.addClasses(ClassA.class);
        GenericArchive archive = ShrinkWrap.create(GenericArchive.class);
        archive.add(new ArchiveAsset(jar, ZipExporter.class), "lib/" + jar.getName());
        archive.add(new FileAsset(new File("src/test/resources/archives/propsB.properties")), "config/propsB.properties");
        return archive;
    }
}
