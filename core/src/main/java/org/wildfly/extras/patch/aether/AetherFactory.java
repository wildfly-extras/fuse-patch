/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.wildfly.extras.patch.aether;

import java.net.URL;
import java.nio.file.Path;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

public interface AetherFactory {

    URL getRepositoryURL();

    Path getLocalRepositoryPath();

    RepositorySystem getRepositorySystem();

    RepositorySystemSession newRepositorySystemSession();

    RemoteRepository getRemoteRepository();
}
