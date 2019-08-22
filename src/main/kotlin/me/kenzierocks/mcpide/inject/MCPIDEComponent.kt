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

package me.kenzierocks.mcpide.inject

import dagger.BindsInstance
import dagger.Component
import me.kenzierocks.mcpide.FxmlFiles
import me.kenzierocks.mcpide.MCPIDE
import me.kenzierocks.mcpide.ModelProcessing
import javax.inject.Singleton

@[Singleton Component(
    modules = [
        CoroutineSupportModule::class,
        CommsModule::class,
        ViewModule::class,
        ModelModule::class,
        HttpModule::class,
        CsvModule::class,
        JsonModule::class,
        XmlModule::class,
        FxModule::class,
        RepositorySystemModule::class,
        ProjectComponent.Module::class
    ]
)]
interface MCPIDEComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun appInstance(mcpide: MCPIDE): Builder

        fun coroutineSupportModule(module: CoroutineSupportModule): Builder
        fun commsModule(module: CommsModule): Builder
        fun viewModule(module: ViewModule): Builder
        fun modelModule(module: ModelModule): Builder
        fun httpModule(module: HttpModule): Builder
        fun csvModule(module: CsvModule): Builder
        fun jsonModule(module: JsonModule): Builder
        fun xmlModule(module: XmlModule): Builder
        fun fxModule(module: FxModule): Builder
        fun mavenModule(module: RepositorySystemModule): Builder
        fun projectModule(module: ProjectComponent.Module): Builder

        fun build(): MCPIDEComponent
    }

    val modelProcessing: ModelProcessing
    val fxmlFiles: FxmlFiles
}