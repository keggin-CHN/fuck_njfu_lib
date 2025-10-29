package scheduler

import (
	"context"
	"log"

	"fucknjfu_lib/internal/config"

	"github.com/robfig/cron/v3"
)

type Scheduler struct {
	cron              *cron.Cron
	cfg               config.Config
	autoReservation   func(context.Context)
	lateProtection    func(context.Context)
	trafficCollection func(context.Context)
}

func New(cfg config.Config) *Scheduler {
	return &Scheduler{
		cron: cron.New(cron.WithLocation(cfg.SchedulerTZ)),
		cfg:  cfg,
	}
}

func (s *Scheduler) SetAutoReservationHandler(handler func(context.Context)) {
	s.autoReservation = handler
}

func (s *Scheduler) SetLateProtectionHandler(handler func(context.Context)) {
	s.lateProtection = handler
}

func (s *Scheduler) SetTrafficCollectionHandler(handler func(context.Context)) {
	s.trafficCollection = handler
}

func (s *Scheduler) Start() error {
	if s.autoReservation != nil && s.cfg.AutoReservation {
		if _, err := s.cron.AddFunc(s.cfg.SchedulerCron, func() {
			s.safeRun("autoReservation", s.autoReservation)
		}); err != nil {
			return err
		}
	}

	if s.lateProtection != nil {
		if _, err := s.cron.AddFunc("*/10 * * * *", func() {
			s.safeRun("lateProtection", s.lateProtection)
		}); err != nil {
			return err
		}
	}

	if s.trafficCollection != nil {
		if _, err := s.cron.AddFunc("*/5 * * * *", func() {
			s.safeRun("trafficCollection", s.trafficCollection)
		}); err != nil {
			return err
		}
	}

	s.cron.Start()
	return nil
}

func (s *Scheduler) Stop() context.Context {
	return s.cron.Stop()
}

func (s *Scheduler) safeRun(name string, handler func(context.Context)) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("scheduler task %s panic: %v", name, r)
		}
	}()

	handler(context.Background())
}
