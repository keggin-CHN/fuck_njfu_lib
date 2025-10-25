import os
from app import app, db

def initialize_database():
    """
    Initializes the database by creating all tables if the database file doesn't exist.
    """
    # Create an application context to interact with the database
    with app.app_context():
        db_path = app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')
        
        # Check if the database file already exists.
        # If it does, we assume it's already initialized.
        if os.path.exists(db_path):
            print("Database file already exists. Skipping initialization.")
            return

        print("Database file not found. Creating all tables...")
        try:
            db.create_all()
            print("Successfully created all database tables.")
        except Exception as e:
            print(f"An error occurred while creating tables: {e}")

if __name__ == "__main__":
    initialize_database()