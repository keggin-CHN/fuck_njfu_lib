package config

import (
	"log"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	AppPort          string
	DatabasePath     string
	JWTSecret        string
	EncryptionSecret string
	AllowedOrigins   []string
	SchedulerTZ      *time.Location
	SchedulerCron    string
	AutoReservation  bool
	FrontendDir      string
}

func Load() Config {
	cfg := Config{}

	cfg.AppPort = getEnv("APP_PORT", "8080")

	cfg.DatabasePath = getEnv("DATABASE_PATH", "data/app.db")

	cfg.JWTSecret = getEnv("JWT_SECRET", "change-me-please")

	encryptionSecret := getEnv("ENCRYPTION_SECRET", "0123456789abcdef0123456789abcdef")
	if len(encryptionSecret) != 32 {
		log.Printf("ENCRYPTION_SECRET 长度不是 32 字节，将自动调整。")
		encryptionSecret = padRight(encryptionSecret, 32)
	}
	cfg.EncryptionSecret = encryptionSecret

	allowedOrigins := getEnv("ALLOWED_ORIGINS", "*")
	if allowedOrigins == "*" {
		cfg.AllowedOrigins = []string{"*"}
	} else {
		cfg.AllowedOrigins = strings.Split(allowedOrigins, ",")
	}

	cfg.FrontendDir = getEnv("FRONTEND_DIST", "public")

	tzName := getEnv("TIMEZONE", "Asia/Shanghai")
	location, err := time.LoadLocation(tzName)
	if err != nil {
		log.Printf("加载时区 %s 失败，使用 UTC: %v", tzName, err)
		location = time.UTC
	}
	cfg.SchedulerTZ = location

	cfg.SchedulerCron = getEnv("SCHEDULER_CRON", "*/5 * * * *")

	autoReservationStr := getEnv("AUTO_RESERVATION_ENABLED", "true")
	autoReservation, err := strconv.ParseBool(autoReservationStr)
	if err != nil {
		autoReservation = true
	}
	cfg.AutoReservation = autoReservation

	return cfg
}

func getEnv(key, def string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return def
	}
	return value
}

func padRight(input string, length int) string {
	if len(input) >= length {
		return input[:length]
	}
	builder := strings.Builder{}
	builder.Grow(length)
	builder.WriteString(input)
	for builder.Len() < length {
		builder.WriteByte('0')
	}
	return builder.String()
}
