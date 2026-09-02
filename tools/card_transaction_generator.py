#!/usr/bin/env python3

import csv
import random
from datetime import datetime
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent.parent

MASTER_FILE = BASE_DIR / "raw" / "customer_master" / "customers.csv"
OUTPUT_DIR = BASE_DIR / "raw" / "card_transactions"
OUTPUT_FILE = OUTPUT_DIR / "transactions.csv"

PRODUCTS = [
    ("Book", "Book A", 1200),
    ("Book", "Book B", 1800),
    ("Electronics", "Keyboard", 8500),
    ("Electronics", "Mouse", 3200),
    ("Food", "Coffee", 500),
    ("Food", "Lunch", 1200),
    ("Stationery", "Notebook", 600),
    ("Stationery", "Pen", 200),
]


def load_cards():
    with open(MASTER_FILE, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return [row["card_number"] for row in reader]


def generate_transactions(cards, count=20):
    transactions = []

    for transaction_id in range(1, count + 1):
        card_number = random.choice(cards)
        category, product, unit_price = random.choice(PRODUCTS)
        quantity = random.randint(1, 5)

        transactions.append({
            "transaction_id": transaction_id,
            "card_number": card_number,
            "purchase_date": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "category": category,
            "product": product,
            "unit_price": unit_price,
            "quantity": quantity,
            "amount": unit_price * quantity
        })

    return transactions


def main():
    cards = load_cards()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    transactions = generate_transactions(cards)

    with open(
        OUTPUT_FILE,
        "w",
        newline="",
        encoding="utf-8"
    ) as f:

        fieldnames = [
            "transaction_id",
            "card_number",
            "purchase_date",
            "category",
            "product",
            "unit_price",
            "quantity",
            "amount"
        ]

        writer = csv.DictWriter(f, fieldnames=fieldnames)

        writer.writeheader()
        writer.writerows(transactions)

    print(f"Generated {len(transactions)} transactions.")
    print(f"Output: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
