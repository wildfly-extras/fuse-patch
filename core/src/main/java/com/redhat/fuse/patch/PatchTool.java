package com.redhat.fuse.patch;

import java.io.IOException;
import java.nio.file.Path;
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
     * Add the given file to the repository
     */
    PatchId add(Path filePath) throws IOException;
    
    /**
     * Install the given patch id to the server
     */
    void install(PatchId patchId) throws IOException;

    /**
     * Update the server for the given patch name
     */
    void update(String symbolicName) throws IOException;
}