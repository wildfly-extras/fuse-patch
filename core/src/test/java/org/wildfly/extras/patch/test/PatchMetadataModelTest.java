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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extras.patch.PatchMetadata;
import org.wildfly.extras.patch.PatchMetadataBuilder;
import org.wildfly.extras.patch.PatchId;
import org.wildfly.extras.patch.internal.PatchMetadataModel;

public class PatchMetadataModelTest {

    @Test
    public void testMarshallUnmarshalConfiguration() throws Exception {

        PatchMetadataBuilder builder = new PatchMetadataBuilder();
        builder.patchId(PatchId.fromString("foo-1.0.0.SP1"));
        builder.oneoffId(PatchId.fromString("foo-1.0.0"));
        builder.dependencies(PatchId.fromString("aaa"), PatchId.fromString("bbb"));
        builder.postCommands("xxx", "yyy");
        PatchMetadata metadata = builder.build();

        JAXBContext jaxbContext = JAXBContext.newInstance(PatchMetadataModel.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        File outfile = new File("target/test-configuration.xml");
        jaxbMarshaller.marshal(PatchMetadataModel.fromPatchMetadata(metadata), new FileOutputStream(outfile));

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        PatchMetadataModel model = (PatchMetadataModel) unmarshaller.unmarshal(new FileInputStream(outfile));
        PatchMetadata was = model.toPatchMetadata();

        Assert.assertEquals(metadata.getPatchId(), was.getPatchId());
        Assert.assertEquals(metadata.getOneoffId(), was.getOneoffId());
        Assert.assertEquals(2, was.getDependencies().size());
        Assert.assertEquals(metadata.getDependencies(), was.getDependencies());
        Assert.assertEquals(2, was.getPostCommands().size());
        Assert.assertEquals(metadata.getPostCommands(), was.getPostCommands());
    }
}
