package com.provinggrounds.compat;

/**
 * 檔案: GunClass.java
 * 目的: 物品的槍械分類（計畫書 B2 快捷欄規則用）。由槍械適配層判斷，核心程式不認識槍械模組。
 *   PISTOL = 手槍（快捷欄第 3 格），LARGE = 其他所有槍（第 1、2 格），NONE = 不是槍。
 */
public enum GunClass { NONE, PISTOL, LARGE }
