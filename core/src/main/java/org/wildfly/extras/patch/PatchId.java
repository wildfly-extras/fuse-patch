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
package org.wildfly.extras.patch;

import java.net.URL;

import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;


/**
 * A patch identity.
 *
 * A  {@link Patch} is identified by name {@link Version}
 *
 * A {@code PatchId} is immutable.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2015
 */
public final class PatchId implements Comparable<PatchId> {

    private final String name;
    private final Version version;
    private final String canonicalForm;

    public static PatchId create(String symbolicName, String version) {
        return new PatchId(symbolicName, version != null ? Version.parseVersion(version) : null);
    }

    public static PatchId create(String symbolicName, Version version) {
        return new PatchId(symbolicName, version);
    }

    public static PatchId fromString(String identity) {
        int index = identity.indexOf('-');
        if (index < 0) {
            return new PatchId(identity, Version.emptyVersion);
        }
        while (index > 0) {
            String namePart = identity.substring(0, index);
            String versionPart = identity.substring(index + 1);
            try {
                Version version = Version.parseVersion(versionPart);
                return new PatchId(namePart, version);
            } catch (RuntimeException ex) {
                index = identity.indexOf('-', index + 1);
            }
        }
        return new PatchId(identity, Version.emptyVersion);
    }

    public static PatchId fromURL(URL url) {
        String name = url.getFile();
        return PatchId.fromString(name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf('.')));
    }

    private PatchId(String name, Version version) {
        IllegalArgumentAssertion.assertNotNull(name, "name");
        IllegalArgumentAssertion.assertTrue(1 == name.split("\\s").length, "Invalid name part: " + name);
        this.name = name.trim();
        this.version = version != null ? version : Version.emptyVersion;
        this.canonicalForm = this.name + "-" + this.version;
    }

    public String getName() {
        return name;
    }

    public Version getVersion() {
        return version;
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
        if (!(obj instanceof PatchId)) return false;
        PatchId other = (PatchId) obj;
        return canonicalForm.equals(other.canonicalForm);
    }

    @Override
    public int compareTo(PatchId other) {
        int result = name.compareTo(other.name);
        if (result == 0) {
            result = version.compareTo(other.version);
        }
        return result;
    }

    @Override
    public String toString() {
        return canonicalForm;
    }
}
