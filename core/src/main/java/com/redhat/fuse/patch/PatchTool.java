package com.redhat.fuse.patch;

import java.io.IOException;
import java.util.List;

/**
 * The patch tool.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Jun-2015
 */
public interface PatchTool {

    List<PatchId> queryServer();

    List<PatchId> queryRepository();

    void install(PatchId patchId) throws IOException;

    void update() throws IOException;

}