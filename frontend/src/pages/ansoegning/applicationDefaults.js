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
