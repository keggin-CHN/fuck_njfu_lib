package handlers

import (
	"fucknjfu_lib/internal/config"
	"fucknjfu_lib/internal/scheduler"

	"gorm.io/gorm"
)

type Handler struct {
	DB        *gorm.DB
	Config    config.Config
	Scheduler *scheduler.Scheduler
}

func New(db *gorm.DB, cfg config.Config, sched *scheduler.Scheduler) *Handler {
	return &Handler{DB: db, Config: cfg, Scheduler: sched}
}

func (h *Handler) InitializeDefaults() {
	_ = h.setSystemSetting("invite_code_required", "true", "是否启用邀请码注册", nil)
	_ = h.setSystemSetting("announcement", "", "系统公告", nil)
}
