package database

import (
	"fmt"
	"os"
	"path/filepath"

	"fucknjfu_lib/internal/config"
	"fucknjfu_lib/internal/models"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func Connect(cfg config.Config) (*gorm.DB, error) {
	if err := os.MkdirAll(filepath.Dir(cfg.DatabasePath), 0o755); err != nil {
		return nil, fmt.Errorf("failed to create database directory: %w", err)
	}

	gormCfg := &gorm.Config{Logger: logger.Default.LogMode(logger.Info)}
	db, err := gorm.Open(sqlite.Open(cfg.DatabasePath), gormCfg)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	if err := migrate(db); err != nil {
		return nil, err
	}

	return db, nil
}

func migrate(db *gorm.DB) error {
	modelsToMigrate := []interface{}{
		&models.User{},
		&models.ReservationSetting{},
		&models.ReservationHistory{},
		&models.InviteCode{},
		&models.SystemSetting{},
		&models.LogEntry{},
		&models.Traffic{},
	}

	for _, model := range modelsToMigrate {
		if err := db.AutoMigrate(model); err != nil {
			return fmt.Errorf("failed to migrate %T: %w", model, err)
		}
	}

	return nil
}
