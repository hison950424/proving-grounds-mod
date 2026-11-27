### 修復
- **修復半磚走上 1 格實心方塊或連續上兩階半磚（雙層半磚）時被反推回去導致上不去的問題**（`BlockCollisionMixin`）：緊鄰下半磚的實心方塊與雙層半磚在提供平滑斜坡碰撞箱（`SlabRampShapes`）時，原本 `isSuffocating` 仍回傳 `true`，導致 Minecraft 客戶端 `LocalPlayer.moveTowardsClosestSpace` 誤判玩家腳部卡在實心方塊內而持續往後推回；現改為比照原版樓梯回傳 `isSuffocating = false`，走上 1 格方塊與連續兩階半磚不再被推回。
