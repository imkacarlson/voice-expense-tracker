## TRUE or FALSE Check for Splitwise

```
Output ONLY a 1 or a 0 to indicate if this transaction descrbed was split with another person.

Ex: "I just got groceries at trader joes for 56 dollars and 90 cents using my Chase Sapphire Preferred card and after splitwise with Emily I will only owe 35 point 45"
>1

Ex: "groceries at trader joes for 56 point 90 dollars using Chase Sapphire Preferred card"
>0

Now: "I just spent 95 bucks at Walmart for birthday gifts for my family and I used my Discover card"
```

## IF Splitwise is TRUE

### Extract Total Amount Charged to my Card Field

```
Extract EXACTLY ONE DOLLAR VALUE for the total amount charged to my card IGNORING any other values.

Ex: "groceries at trader joes for 56.90 dollars using Chase Sapphire Preferred card and after splitwise with Emily I will only owe 35.78"
>56.90

Ex: "I just spent 95 dollars at Walmart for birthday gifts for my family and I used my Discover card after splitting I'll owe 56.79"
>95

Now: "I just got gas that costed 45 point 64 after I split with Emily I'll owe 24.34"
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
