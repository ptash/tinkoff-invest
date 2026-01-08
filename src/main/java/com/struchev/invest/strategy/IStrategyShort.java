package com.struchev.invest.strategy;

public interface IStrategyShort {
    public void setShort();
    public void setExtName(String name);
    public IStrategyShort clone();
}
