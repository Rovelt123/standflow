import { useState } from 'react'
import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import StatTile from '../../components/admin/StatTile.jsx'
import { statusLabels } from '../../components/admin/statusLabels.js'
import ApplicationsToolbar from '../../components/admin/ApplicationsToolbar.jsx'
import ApplicationsTable from '../../components/admin/ApplicationsTable.jsx'
import { applications, standTypes, stats } from './mockData.js'
import styles from './KontrolpanelPage.module.css'

const csvColumns = [
  { header: 'Virksomhed', key: 'company' },
  { header: 'Kontaktperson', key: 'contact' },
  { header: 'Standtype', key: 'standType' },
  { header: 'Status', key: 'status' },
  { header: 'Dato', key: 'date' },
]

//--------------------------------------------------------------

function toCsvValue(value) {
  return `"${String(value).replaceAll('"', '""')}"`
}

//--------------------------------------------------------------

function downloadCsv(rows) {
  const lines = [
    csvColumns.map((column) => toCsvValue(column.header)).join(';'),
    ...rows.map((row) =>
      csvColumns
        .map((column) => toCsvValue(column.key === 'status' ? statusLabels[row.status] : row[column.key]))
        .join(';'),
    ),
  ]
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'ansoegninger.csv'
  link.click()
  URL.revokeObjectURL(url)
}

//--------------------------------------------------------------

function KontrolpanelPage() {
  const [search, setSearch] = useState('')
  const [standType, setStandType] = useState('')

  const query = search.trim().toLowerCase()
  const filteredApplications = applications.filter(
    (application) =>
      (standType === '' || application.standType === standType) &&
      (application.company.toLowerCase().includes(query) || application.contact.toLowerCase().includes(query)),
  )

  return (
    <div className={styles.page}>
      <AdminTopBar title="Kontrolpanel" />
      <AdminSidebar />
      <main className={styles.main}>
        <div className={styles.stats}>
          {stats.map((stat) => (
            <StatTile key={stat.label} {...stat} />
          ))}
        </div>
        <ApplicationsToolbar
          search={search}
          onSearchChange={setSearch}
          standType={standType}
          onStandTypeChange={setStandType}
          standTypes={standTypes}
          onExport={() => downloadCsv(filteredApplications)}
        />
        <ApplicationsTable applications={filteredApplications} />
      </main>
    </div>
  )
}

export default KontrolpanelPage
