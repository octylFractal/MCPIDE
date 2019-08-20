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
        JavaParserModule::class,
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
        fun parserModule(module: JavaParserModule): Builder
        fun projectModule(module: ProjectComponent.Module): Builder

        fun build(): MCPIDEComponent
    }

    val modelProcessing: ModelProcessing
    val fxmlFiles: FxmlFiles
}