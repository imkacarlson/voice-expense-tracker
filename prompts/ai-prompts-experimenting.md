## TRUE or FALSE Check for Splitwise

```
Output ONLY a 1 or a 0 to indicate if this transaction descrbed was spit with another person.

Ex: "I just got groceries at trader joes for 56.90 dollars using Chase Sapphire Preferred card and after splitwise with Emily I will only owe 35"
>1

Ex: "groceries at trader joes for 56.90 dollars using Chase Sapphire Preferred card"
>0

Now: "I just spent 95 dollars at Walmart for birthday gifts for my family and I used my Discover card"

```

## (IF Splitwise is TRUE) Total Amount Charged to my Card Field

```
Output ONLY a number for the total amount I spent not counting any splitting that happened. Never prose or any words.

Ex: "groceries at trader joes for 56.90 dollars using Chase Sapphire Preferred card"
56.90

Ex: "groceries at trader joes for 56.90 dollars using Chase Sapphire Preferred card and after splitwise with Emily I will only owe 35"
56.90

Now: "I just spent 95 dollars at Walmart for birthday gifts for my family and I used my Discover card"
```

## (IF Splitwise is FALSE) Total Amount Charged to my Card Field

```
Output ONLY a number for the amount spent. Never prose or any words.

Ex: "groceries at trader joes for 56.90 dollars using Chase Sapphire Preferred card"
56.90

Now: "I just spent 95 dollars at Walmart for birthday gifts for my family and I used my Discover card"
```
