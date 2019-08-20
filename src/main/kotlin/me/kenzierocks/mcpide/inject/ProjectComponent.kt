package me.kenzierocks.mcpide.inject

import dagger.BindsInstance
import dagger.Subcomponent
import me.kenzierocks.mcpide.fx.JavaEditorAreaCreator
import me.kenzierocks.mcpide.project.Project
import java.nio.file.Path

@Subcomponent(
    modules = [

    ]
)
@ProjectScope
interface ProjectComponent {

    @dagger.Module(
        subcomponents = [ProjectComponent::class]
    )
    companion object Module

    val project: Project
    val javaEditorAreaCreator: JavaEditorAreaCreator

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun projectDirectory(@ProjectQ directory: Path) : Builder

        fun build() : ProjectComponent
    }
}