package com.ankipro.model;

/**
 * Created by Hyde on 14/06/16.
 */
public class Produto {
    private int id = 0;
    private String key = "";
    private boolean mine = false;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setMine(boolean mine){
        this.mine = mine;
    }

    public boolean isMine(){
        return this.mine;
    }
}
