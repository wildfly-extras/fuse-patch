package org.wildfly.extras.patch;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * The patch tool.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public interface PatchTool {

    /**
     * Query the server for installed patches
     */
    List<PatchId> queryServer();

    /**
     * Query the repository for available patches
     */
    List<PatchId> queryRepository();

    /**
     * Add the given archive to the repository
     */
    PatchId add(URL archiveUrl) throws IOException;
    
    /**
     * Add a post install command for the given patch id
     */
    void addPostCommand(PatchId patchId, String cmd);
    
    /**
     * Install the given patch id to the server
     */
    PatchSet install(PatchId patchId) throws IOException;

    /**
     * Update the server for the given patch name
     */
    PatchSet update(String symbolicName) throws IOException;
}