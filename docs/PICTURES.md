# Food pictures

All 25 menu items use real photos: `app/src/main/res/drawable-nodpi/food_<id>.jpg` (the id is the item id from the
table below). The photos are about 245 px square, which is sharp in the menu and cart lists but a little soft on the
item detail screen. Swap in larger photos (about 800 px) when you have them.

## Replacing a photo

1. Use a photo you have the right to use (square works best, about 800 px wide, under 300 KB).
2. Name it `food_<id>.jpg` (lowercase, no spaces) and replace the file in `app/src/main/res/drawable-nodpi/`.
   If you use `.png` or `.webp` instead, delete the old `.jpg` so two files do not share a name.
3. Rebuild the app. No code or database change is needed.

An item with no `food_<id>` file falls back to its emoji.

| Item id | Menu item | File name |
|---|---|---|
| 1 | Quarter Chicken | `food_1.jpg` |
| 2 | Rump 300g | `food_2.jpg` |
| 3 | 6 Wings | `food_3.jpg` |
| 4 | Cheese Burger | `food_4.jpg` |
| 5 | Malva Pudding | `food_5.jpg` |
| 6 | Coca Cola 330ml | `food_6.jpg` |
| 7 | Sprite 330ml | `food_7.jpg` |
| 8 | Still Water 500ml | `food_8.jpg` |
| 9 | Pap & Gravy | `food_9.jpg` |
| 10 | Chips | `food_10.jpg` |
| 11 | Margherita Pizza | `food_11.jpg` |
| 12 | Chicken Wrap | `food_12.jpg` |
| 13 | Half Chicken | `food_13.jpg` |
| 14 | Fillet 250g | `food_14.jpg` |
| 15 | T-Bone 400g | `food_15.jpg` |
| 16 | 12 Wings | `food_16.jpg` |
| 17 | Double Cheese Burger | `food_17.jpg` |
| 18 | Crumbed Burger | `food_18.jpg` |
| 19 | Ice Cream Cup | `food_19.jpg` |
| 20 | Fanta Orange 330ml | `food_20.jpg` |
| 21 | Pepperoni Pizza | `food_21.jpg` |
| 22 | Coleslaw | `food_22.jpg` |
| 23 | Steak Wrap | `food_23.jpg` |
| 24 | Family Combo | `food_24.jpg` |
| 25 | Solo Combo | `food_25.jpg` |

## Specials pictures (Home screen)

The 10 specials cards use `promo_<id>.jpg` in `app/src/main/res/drawable-nodpi/` (ids 1 to 10, in the order the
specials appear: Family Combo, Solo Combo, Earn as you eat, Review & earn, Rewards waiting, Flame-grilled favourite,
Steak lovers, Wing time, Sweet finish, Order ahead). A special without a picture shows its emoji.
