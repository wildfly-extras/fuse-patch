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

import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;

import org.wildfly.extras.patch.Record;
import org.wildfly.extras.patch.SmartPatch;

public class SmartPatchAdapter {

    private DataHandler dataHandler;
    private PatchAdapter patch;
    private String[] removeRecs;
    private String[] replaceRecs;
    private String[] addRecs;

    public static SmartPatchAdapter fromSmartPatch(SmartPatch smartPatch) {
        SmartPatchAdapter result = new SmartPatchAdapter();
        result.dataHandler = smartPatch.getDataHandler();
        result.patch = PatchAdapter.fromPatch(smartPatch.getPatch());
        List<Record> removeSet = new ArrayList<Record>(smartPatch.getRemoveSet());
        result.removeRecs = new String[removeSet.size()];
        for (int i = 0; i < removeSet.size(); i++) {
            result.removeRecs[i] = removeSet.get(i).toString();
        }
        List<Record> replaceSet = new ArrayList<Record>(smartPatch.getReplaceSet());
        result.replaceRecs = new String[replaceSet.size()];
        for (int i = 0; i < replaceSet.size(); i++) {
            result.replaceRecs[i] = replaceSet.get(i).toString();
        }
        List<Record> addSet = new ArrayList<Record>(smartPatch.getAddSet());
        result.addRecs = new String[addSet.size()];
        for (int i = 0; i < addSet.size(); i++) {
            result.addRecs[i] = addSet.get(i).toString();
        }
        return result;
    }
    
    public SmartPatch toSmartPatch() {
        if (dataHandler != null) {
            return SmartPatch.forInstall(patch.toPatch(), dataHandler);
        } else {
            return SmartPatch.forUninstall(patch.toPatch());
        }
    }

    public DataHandler getDataHandler() {
        return dataHandler;
    }

    public void setDataHandler(DataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    public PatchAdapter getPatch() {
        return patch;
    }

    public void setPatch(PatchAdapter patch) {
        this.patch = patch;
    }

    public String[] getRemoveRecs() {
        return removeRecs;
    }

    public void setRemoveRecs(String[] removeRecs) {
        this.removeRecs = removeRecs;
    }

    public String[] getReplaceRecs() {
        return replaceRecs;
    }

    public void setReplaceRecs(String[] updateRecs) {
        this.replaceRecs = updateRecs;
    }

    public String[] getAddRecs() {
        return addRecs;
    }

    public void setAddRecs(String[] addRecs) {
        this.addRecs = addRecs;
    }

}
