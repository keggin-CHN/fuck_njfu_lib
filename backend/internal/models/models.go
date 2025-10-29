package models

import (
	"time"
)

type User struct {
	ID               uint               `gorm:"primaryKey" json:"id"`
	Username         string             `gorm:"uniqueIndex;size:64" json:"username"`
	PasswordHash     string             `json:"-"`
	EduPassword      string             `json:"-"`
	LibPassword      string             `json:"-"`
	IsAdmin          bool               `json:"isAdmin"`
	NotificationType string             `gorm:"size:32" json:"notificationType"`
	WebhookURL       string             `gorm:"size:512" json:"webhookUrl"`
	CreatedAt        time.Time          `json:"createdAt"`
	UpdatedAt        time.Time          `json:"updatedAt"`
	Settings         ReservationSetting `gorm:"constraint:OnDelete:CASCADE" json:"settings"`
}

type ReservationSetting struct {
	ID           uint      `gorm:"primaryKey" json:"id"`
	UserID       uint      `gorm:"uniqueIndex" json:"userId"`
	Area         string    `gorm:"size:64" json:"area"`
	SeatNumber   int       `json:"seatNumber"`
	StartTime    string    `gorm:"size:16" json:"startTime"`
	EndTime      string    `gorm:"size:16" json:"endTime"`
	AutoReserve  bool      `json:"autoReserve"`
	PreventLate  bool      `json:"preventLate"`
	AutoFindSeat bool      `json:"autoFindSeat"`
	UpdatedAt    time.Time `json:"updatedAt"`
	CreatedAt    time.Time `json:"createdAt"`
}

const (
	ReservationStatusSuccess    = "成功"
	ReservationStatusFailed     = "失败"
	ReservationStatusAuthFailed = "auth_failed"
)

type ReservationHistory struct {
	ID               uint      `gorm:"primaryKey" json:"id"`
	UserID           uint      `gorm:"index" json:"userId"`
	Area             string    `gorm:"size:64" json:"area"`
	SeatNumber       int       `json:"seatNumber"`
	SeatID           int       `json:"seatId"`
	ReserveDate      time.Time `json:"reserveDate"`
	StartTime        string    `gorm:"size:16" json:"startTime"`
	EndTime          string    `gorm:"size:16" json:"endTime"`
	Status           string    `gorm:"size:32" json:"status"`
	Message          string    `gorm:"type:text" json:"message"`
	UUID             string    `gorm:"size:64" json:"uuid"`
	IsLateProtection bool      `json:"isLateProtection"`
	IsAutoFind       bool      `json:"isAutoFind"`
	CreatedAt        time.Time `json:"createdAt"`
}

type InviteCode struct {
	ID        uint       `gorm:"primaryKey" json:"id"`
	Code      string     `gorm:"size:8;uniqueIndex" json:"code"`
	CreatedBy uint       `json:"createdBy"`
	UsedBy    *uint      `json:"usedBy"`
	CreatedAt time.Time  `json:"createdAt"`
	UsedAt    *time.Time `json:"usedAt"`
	IsUsed    bool       `json:"isUsed"`
}

type SystemSetting struct {
	ID          uint      `gorm:"primaryKey" json:"id"`
	Key         string    `gorm:"size:64;uniqueIndex" json:"key"`
	Value       string    `gorm:"type:text" json:"value"`
	Description string    `gorm:"size:256" json:"description"`
	UpdatedAt   time.Time `json:"updatedAt"`
	UpdatedBy   *uint     `json:"updatedBy"`
}

type LogEntry struct {
	ID           uint      `gorm:"primaryKey" json:"id"`
	UserID       *uint     `json:"userId"`
	IPAddress    string    `gorm:"size:64" json:"ipAddress"`
	Action       string    `gorm:"size:256" json:"action"`
	UserAgent    string    `gorm:"size:512" json:"userAgent"`
	ResponseCode int       `json:"responseCode"`
	ResponseBody string    `gorm:"type:text" json:"responseBody"`
	ErrorMessage string    `gorm:"type:text" json:"errorMessage"`
	CreatedAt    time.Time `json:"createdAt"`
}

type Traffic struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	Timestamp int64     `gorm:"uniqueIndex" json:"timestamp"`
	Count     int       `json:"count"`
	CreatedAt time.Time `json:"createdAt"`
}
