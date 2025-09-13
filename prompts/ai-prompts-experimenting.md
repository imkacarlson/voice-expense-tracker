## TRUE or FALSE Check for Splitwise

```
Output ONLY a 1 or a 0 to indicate if this transaction descrbed was split with another person.

Ex: "I just got groceries at trader joes for 56 point 90 using Chase Sapphire Preferred card and after splitwise with Emily I will only owe 35 point 45"
>1

Ex: "groceries at trader joes for 56 point 90 dollars using Chase Sapphire Preferred card"
>0

Now: "I just spent 95 bucks at Walmart for birthday gifts for my family and I used my Discover card"

```

## IF Splitwise is TRUE

### Extract Total Amount Charged to my Card Field

```
Extract ONLY ONE DOLLAR VALUE for the total amount charged to my card not counting any splitting.

Ex: "groceries at trader joes for 56.90 dollars using Chase Sapphire Preferred card and after splitwise with Emily I will only owe 35.78"
>56.90

Now: "I just spent 95 dollars at Walmart for birthday gifts for my family and I used my Discover card after splitting I'll owe 56.79"
```

### Extract Total Amount I Owe Field

```
TODO
```

## (IF Splitwise is FALSE)

### Total Amount Charged to my Card Field

```
Output ONLY a number for the amount spent. Never prose or any words.
TODO
```
