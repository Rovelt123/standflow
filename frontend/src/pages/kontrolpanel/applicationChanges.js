export async function saveApplicationChanges(application, values, request, onUpdated) {
  const path = `/applications/${application.id}`
  if (values.comment !== (application.comment ?? '') || values.customerNote !== (application.customerNote ?? '')) {
    onUpdated(await request(`${path}/comment`, 'PUT', { comment: values.comment, customerNote: values.customerNote }))
  }
  if (values.status !== application.status) {
    onUpdated(await request(`${path}/status`, 'PATCH', { status: values.status }))
  }
  if (values.standType !== application.standType) {
    const result = await request(`${path}/stand-type`, 'PATCH', { standType: values.standType })
    onUpdated(result.application)
    return result.notificationStatus
  }
  return 'UNCHANGED'
}
