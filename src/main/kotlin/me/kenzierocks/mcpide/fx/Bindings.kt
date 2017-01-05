package me.kenzierocks.mcpide.fx

import javafx.beans.binding.Bindings
import javafx.beans.binding.ObjectBinding
import javafx.beans.value.ObservableObjectValue

fun <I, O> ObservableObjectValue<I>.map(func: (I) -> O): ObjectBinding<O> {
    return Bindings.createObjectBinding({
        func(this.value)
    }, arrayOf(this))
}