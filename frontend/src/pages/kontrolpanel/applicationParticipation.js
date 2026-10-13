export function hasParticipatedBefore(application) {
  return application.previousExhibitor === true || (application.history?.length ?? 0) > 0
}
