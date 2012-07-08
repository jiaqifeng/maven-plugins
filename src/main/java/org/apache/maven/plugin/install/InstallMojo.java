package org.apache.maven.plugin.install;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Installs the project's main artifact, and any other artifacts attached by other plugins in the lifecycle, to the local repository.
 *
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id$
 */
@Mojo( name = "install", defaultPhase = LifecyclePhase.INSTALL, threadSafe = true )
public class InstallMojo
    extends AbstractInstallMojo
{
    /**
     */
    @Parameter( defaultValue = "${project.packaging}", required = true, readonly = true )
    protected String packaging;

    /**
     */
    @Parameter( defaultValue = "${project.file}", required = true, readonly = true )
    private File pomFile;

    /**
     * Set this to <code>true</code> to bypass artifact installation. Use this for artifacts that does not need to be installed in the local repository.
     */
    @Parameter( property = "maven.install.skip", defaultValue = "false", required = true )
    private boolean skip;

    /**
     */
    @Parameter( defaultValue = "${project.artifact}", required = true, readonly = true )
    private Artifact artifact;

    /**
     */
    @Parameter( defaultValue = "${project.attachedArtifacts}", required = true, readonly = true )
    private List attachedArtifacts;

    public void execute()
        throws MojoExecutionException
    {
        if ( skip )
        {
            getLog().info( "Skipping artifact installation" );
            return;
        }

        // TODO: push into transformation
        boolean isPomArtifact = "pom".equals( packaging );

        ArtifactMetadata metadata = null;

        if ( updateReleaseInfo )
        {
            artifact.setRelease( true );
        }

        try
        {
            Collection metadataFiles = new LinkedHashSet();

            if ( isPomArtifact )
            {
                installer.install( pomFile, artifact, localRepository );
                installChecksums( artifact, metadataFiles );
            }
            else
            {
                metadata = new ProjectArtifactMetadata( artifact, pomFile );
                artifact.addMetadata( metadata );

                File file = artifact.getFile();

                // Here, we have a temporary solution to MINSTALL-3 (isDirectory() is true if it went through compile
                // but not package). We are designing in a proper solution for Maven 2.1
                if ( file != null && file.isFile() )
                {
                    installer.install( file, artifact, localRepository );
                    installChecksums( artifact, metadataFiles );
                }
                else if ( !attachedArtifacts.isEmpty() )
                {
                    getLog().info( "No primary artifact to install, installing attached artifacts instead." );

                    Artifact pomArtifact =
                        artifactFactory.createProjectArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                                                               artifact.getBaseVersion() );
                    pomArtifact.setFile( pomFile );
                    if ( updateReleaseInfo )
                    {
                        pomArtifact.setRelease( true );
                    }

                    installer.install( pomFile, pomArtifact, localRepository );
                    installChecksums( pomArtifact, metadataFiles );
                }
                else
                {
                    throw new MojoExecutionException(
                        "The packaging for this project did not assign a file to the build artifact" );
                }
            }

            for ( Iterator i = attachedArtifacts.iterator(); i.hasNext(); )
            {
                Artifact attached = (Artifact) i.next();

                installer.install( attached.getFile(), attached, localRepository );
                installChecksums( attached, metadataFiles );
            }

            installChecksums( metadataFiles );
        }
        catch ( ArtifactInstallationException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    public void setSkip( boolean skip )
    {
        this.skip = skip;
    }
}
