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

import java.io.IOException;

import javax.activation.DataHandler;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.soap.MTOM;

@MTOM
@WebService(targetNamespace = RepositoryService.TARGET_NAMESPACE)
public interface RepositoryService {

    String TARGET_NAMESPACE = "http://jaxws.patch.extras.wildfly.org/";
    QName SERVICE_QNAME = new QName(TARGET_NAMESPACE, "RepositoryEndpointService");

    /**
     * Get the list of available patches
     * @param prefix The patch name prefix - null for all patches
     * @return An array of available patches
     */
    @WebMethod
    String[] queryAvailable(String prefix);

    /**
     * Get the latest available patches for the given prefix
     * @param prefix The mandatory patch name prefix
     * @return The latest available patch
     */
    @WebMethod
    String getLatestAvailable(String prefix);

    /**
     * Get the patch set for the given id
     * @param patchId The patch id
     * @return The patch set for the given id
     */
    @WebMethod
    PatchAdapter getPatch(String patchId);

    /**
     * Add the given patch archive
     * @param metadata The package metadata
     * @param dataHandler The data of the patch archive
     * @param force Force the add operation
     * @return The patch id
     * @throws java.io.IOException If an IO exception occurred
     */
    @WebMethod
    String addArchive(PatchMetadataAdapter metadata, DataHandler dataHandler, boolean force) throws IOException;

    /**
     * Remove the given patch id
     * @param removeId The id of the archive to remove
     * @return true or false depending on whether the archive was removed
     */
    @WebMethod
    boolean removeArchive(String removeId);

    /**
     * Get the smart patch for the given seed.
     * @param seedPatch The patch set obtained from the server - may be null
     * @param patchId The target patch id - null for the latest
     * @return The smart patch
     */
    @WebMethod
    SmartPatchAdapter getSmartPatch(PatchAdapter seedPatch, String patchId);
}
