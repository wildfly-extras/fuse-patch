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
package org.wildfly.extras.patch.aether;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

public abstract class DefaultAetherFactory implements AetherFactory {

    private RepositorySystem system;
    private RemoteRepository repository;
    private LocalRepository localRepo;
    
    @Override
    public RepositorySystem getRepositorySystem() {
        if (system == null) {
            system = ManualRepositorySystemFactory.newRepositorySystem();
            repository = new RemoteRepository.Builder("fusepatch.repository", "default", getRepositoryURL().toString()).build();
            localRepo = new LocalRepository(getLocalRepositoryPath());
        }
        return system;
    }

    @Override
    public RepositorySystemSession newRepositorySystemSession() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setTransferListener(new ConsoleTransferListener(System.out));
        session.setRepositoryListener(new ConsoleRepositoryListener(System.out));
        return session;
    }

    @Override
    public RemoteRepository getRemoteRepository() {
        return repository;
    }
}
