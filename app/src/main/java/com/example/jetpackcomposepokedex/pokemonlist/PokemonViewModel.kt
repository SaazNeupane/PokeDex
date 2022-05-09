package com.example.jetpackcomposepokedex.pokemonlist

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.example.jetpackcomposepokedex.data.models.PokedexListEntry
import com.example.jetpackcomposepokedex.data.remote.repository.PokemonRepository
import com.example.jetpackcomposepokedex.util.Constants.PAGE_SIZE
import com.example.jetpackcomposepokedex.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class PokemonViewModel @Inject constructor(
    private val pokemonRepository: PokemonRepository
) : ViewModel() {

    private var curPage = 0

    var pokemonList = mutableStateOf<List<PokedexListEntry>>(listOf())
    var loadError = mutableStateOf("")
    var isLoading = mutableStateOf(false)
    var endReached = mutableStateOf(false)

    private var cachedPokemonList = listOf<PokedexListEntry>()
    private var isSearchStarting = true
    var isSearching = mutableStateOf(false)

    init{
        loadPokemonPaginated()
    }

    fun searchPokemon(query: String) {

        val listToSearch = if(isSearchStarting){
            pokemonList.value
        } else {
            cachedPokemonList
        }

        viewModelScope.launch(Dispatchers.Default) {
            if(query.isEmpty()){
                pokemonList.value = cachedPokemonList
                isSearching.value = false
                isSearchStarting = true
                return@launch
            }
            val results = listToSearch.filter {
                it.pokemonName!!.contains(query.trim(), true) ||
                        it.id.toString() == query.trim()
            }
            if(isSearchStarting){
                cachedPokemonList = pokemonList.value
                isSearchStarting = false
            }
            pokemonList.value = results
            isSearching.value = true
        }
    }

    fun loadPokemonPaginated(){
        viewModelScope.launch {
            isLoading.value = true
            val result = pokemonRepository.getPokemonList(PAGE_SIZE, curPage * PAGE_SIZE)

            when(result){
                is Resource.Success -> {
                    endReached.value = curPage * PAGE_SIZE >= result.data!!.count
                    val pokemonEntries = result.data.results.mapIndexed { index, entry ->
                        val number = if(entry.url.endsWith("/")){
                            entry.url.dropLast(1).takeLastWhile { it.isDigit()}
                        }
                        else{
                            entry.url.takeLastWhile { it.isDigit()}
                        }
                        val url = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$number.png"

                        PokedexListEntry(entry.name.capitalize(Locale.ROOT), number.toInt(),url)
                    }
                    curPage++

                    loadError.value = ""

                    isLoading.value = false

                    pokemonList.value += pokemonEntries
                }
                is Resource.Error -> {
                    loadError.value = result.message!!
                    isLoading.value = false
                }
                else -> {
                    loadError.value = "Unknown error"
                    isLoading.value = false
                }
            }
        }
    }

    fun calcDominantColor(drawable: Drawable, onFinish: (Color) -> Unit) {
        val bmp = (drawable as BitmapDrawable).bitmap.copy(Bitmap.Config.ARGB_8888, true)

        Palette.from(bmp).generate { palette ->
            palette?.dominantSwatch?.rgb?.let { it ->
                onFinish(Color(it))
            }
        }
    }

}