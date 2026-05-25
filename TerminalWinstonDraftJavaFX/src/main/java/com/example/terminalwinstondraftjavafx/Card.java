package com.example.terminalwinstondraftjavafx;

// Card holds name + set code so we can fetch the correct image from Scryfall
public class Card {
    public String name;
    public String setCode;

    public Card(String name, String setCode) {
        this.name    = name;
        this.setCode = setCode;
    }

    @Override
    public String toString() {
        return name;
    }
}
