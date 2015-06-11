/*
 * #%L
 * Gravia :: Resource
 * %%
 * Copyright (C) 2010 - 2014 JBoss by Red Hat
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
package com.redhat.fuse.patch;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.redhat.fuse.patch.utils.IllegalArgumentAssertion;


/**
 * An artefact identity.
 *
 * An artefact is identified by its path and checksum
 *
 * An {@code ArtefactId} is immutable.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class ArtefactId {

    private final Path path;
    private final Long checksum;
    private final String canonicalForm;

    public static ArtefactId create(Path path, Long checksum) {
        return new ArtefactId(path, checksum);
    }

    public static ArtefactId fromString(String identity) {
        int index = identity.indexOf(' ');
        String namePart = index > 0 ? identity.substring(0, index) : identity;
        Long checksum = index > 0 ? Long.parseLong(identity.substring(index + 1)) : null;
        return new ArtefactId(Paths.get(namePart), checksum);
    }

    private ArtefactId(Path path, Long checksum) {
        IllegalArgumentAssertion.assertNotNull(path, "path");
        this.path = path;
        this.checksum = checksum;
        this.canonicalForm = path + " " + checksum;
    }


    public Path getPath() {
		return path;
	}

	public Long getChecksum() {
		return checksum;
	}

	public String getCanonicalForm() {
        return canonicalForm;
    }

    @Override
    public int hashCode() {
        return canonicalForm.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof ArtefactId)) return false;
        ArtefactId other = (ArtefactId) obj;
        return canonicalForm.equals(other.canonicalForm);
    }

    @Override
    public String toString() {
        return canonicalForm;
    }
}
