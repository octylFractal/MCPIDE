/*
 * This file is part of MCPIDE, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package me.kenzierocks.mcpide.resolver

import me.kenzierocks.mcpide.data.FileCache
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader
import org.apache.maven.repository.internal.DefaultVersionRangeResolver
import org.apache.maven.repository.internal.DefaultVersionResolver
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.ArtifactDescriptorReader
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.impl.MetadataGeneratorFactory
import org.eclipse.aether.impl.VersionRangeResolver
import org.eclipse.aether.impl.VersionResolver
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.wagon.WagonProvider
import org.eclipse.aether.transport.wagon.WagonTransporterFactory
import org.koin.dsl.module

private inline fun <reified R, reified T : R> DefaultServiceLocator.addService() {
    addService(R::class.java, T::class.java)
}

/**
 * Module responsible for resolving the entire [RepositorySystem] graph.
 */
val resolverModule = module {
    single<RepositorySystem> {
        DefaultServiceLocator().run {
            setErrorHandler(object : DefaultServiceLocator.ErrorHandler() {
                override fun serviceCreationFailed(type: Class<*>, impl: Class<*>, exception: Throwable) {
                    throw exception
                }
            })
            // Maven-like setup, from maven-resolver-provider
            addService<ArtifactDescriptorReader, DefaultArtifactDescriptorReader>()
            addService<VersionResolver, DefaultVersionResolver>()
            addService<VersionRangeResolver, DefaultVersionRangeResolver>()
            addService<MetadataGeneratorFactory, SnapshotMetadataGeneratorFactory>()
            addService<MetadataGeneratorFactory, VersionsMetadataGeneratorFactory>()

            // maven-resolver-connector-basic
            addService<RepositoryConnectorFactory, BasicRepositoryConnectorFactory>()

            // maven-resolver-transport-wagon
            addService<TransporterFactory, WagonTransporterFactory>()
            setServices(WagonProvider::class.java, OkHttpWagonProvider(get()))
            getService(RepositorySystem::class.java)
        }
    }
    single {
        RemoteRepositories(listOf(
            RemoteRepository.Builder("forge-maven", "default", "https://files.minecraftforge.net/maven")
        ).map { it.build() })
    }
    factory<RepositorySystemSession> {
        val fc = get<FileCache>()
        val rs = get<RepositorySystem>()
        MavenRepositorySystemUtils.newSession().also { sess ->
            sess.localRepositoryManager = rs.newLocalRepositoryManager(sess,
                LocalRepository(fc.mavenCacheDirectory.toFile())
            )
        }
    }
}

class RemoteRepositories(
    private val repositories: List<RemoteRepository>
) : List<RemoteRepository> by repositories
