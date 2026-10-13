export function prefillApplication(values, user, editedFields) {
  const defaults = {
    company: user.company,
    contact: [user.firstname, user.lastname].filter(Boolean).join(' '),
    cvr: user.cvr,
    email: user.email,
    phone: user.phone,
    address: user.address,
    city: user.city,
  }
  return {
    ...values,
    ...Object.fromEntries(Object.entries(defaults)
      .filter(([field, value]) => !editedFields.has(field) && typeof value === 'string')),
  }
}

//--------------------------------------------------------------

export function reusableApplicationValues(previous, availableStandTypes) {
  const textFields = ['company', 'contact', 'cvr', 'email', 'phone', 'address', 'city', 'website', 'products']
  return {
    ...Object.fromEntries(textFields.map(field => [field, previous?.[field] ?? ''])),
    standType: availableStandTypes.includes(previous?.standType) ? previous.standType : '',
    tables: Math.min(100, Math.max(0, previous?.tables ?? 0)),
    chairs: Math.min(100, Math.max(0, previous?.chairs ?? 0)),
    previousExhibitor: true,
  }
}
