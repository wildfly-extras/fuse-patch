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
package org.wildfly.extras.patch.internal;

import java.io.File;
import java.io.IOException;

import org.wildfly.extras.patch.Identity;
import org.wildfly.extras.patch.Package;
import org.wildfly.extras.patch.Record;

public final class ParserAccess {

    public static Package getPatchSet(File zipFile) throws IOException {
        Identity patchId = Identity.fromFile(zipFile);
        return Parser.buildPatchSetFromZip(patchId, Record.Action.ADD, zipFile);
    }

}
